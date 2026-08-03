# One shared repository across environments — images are tagged by commit
# SHA (see .github/workflows/ci-cd.yml) and promoted staging -> production
# by reference, not rebuilt per environment.

resource "aws_ecr_repository" "this" {
  name                 = var.repository_name
  image_tag_mutability = "IMMUTABLE" # a given commit SHA tag can never be silently overwritten

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = var.tags
}

# Keep the repository from growing unbounded — untagged images (dangling
# layers from failed/superseded builds) are the only thing safe to expire
# automatically; tagged images are the deploy history and stay.
resource "aws_ecr_lifecycle_policy" "expire_untagged" {
  repository = aws_ecr_repository.this.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Expire untagged images after 14 days"
      selection = {
        tagStatus   = "untagged"
        countType   = "sinceImagePushed"
        countUnit   = "days"
        countNumber = 14
      }
      action = { type = "expire" }
    }]
  })
}