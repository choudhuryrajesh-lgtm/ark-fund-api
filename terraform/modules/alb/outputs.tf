output "alb_arn" {
  value = aws_lb.this.arn
}

output "listener_arn" {
  description = "Whichever listener was actually created — HTTPS if certificate_arn was supplied, plain HTTP otherwise. See main.tf."
  value       = one(concat(aws_lb_listener.https[*].arn, aws_lb_listener.http[*].arn))
}

output "target_group_arn" {
  value = aws_lb_target_group.app.arn
}

output "dns_name" {
  value = aws_lb.this.dns_name
}

output "alb_arn_suffix" {
  description = "For the ecs module's ALBRequestCountPerTarget autoscaling policy resource_label."
  value       = aws_lb.this.arn_suffix
}

output "target_group_arn_suffix" {
  value = aws_lb_target_group.app.arn_suffix
}