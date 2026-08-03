output "endpoint" {
  value = aws_db_instance.this.address
}

output "port" {
  value = aws_db_instance.this.port
}

output "db_url_secret_arn" {
  value = aws_secretsmanager_secret.db_url.arn
}

output "db_username_secret_arn" {
  value = aws_secretsmanager_secret.db_username.arn
}

output "db_password_secret_arn" {
  value = aws_secretsmanager_secret.db_password.arn
}