# Target-tracking on ALBRequestCountPerTarget, sized in
# docs/06-Resiliency-Scalability.md §2: target 120 req/min/target, fast
# scale-out (60s cooldown) to absorb the quarter-end statement-release
# spike from docs/02-Capacity-Planning.md §3, slow scale-in (300s) to avoid
# flapping once it subsides.

resource "aws_appautoscaling_target" "ecs" {
  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.app.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = var.min_capacity
  max_capacity       = var.max_capacity
}

resource "aws_appautoscaling_policy" "request_count" {
  name               = "${var.name}-request-count-tracking"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.ecs.service_namespace
  resource_id        = aws_appautoscaling_target.ecs.resource_id
  scalable_dimension = aws_appautoscaling_target.ecs.scalable_dimension

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ALBRequestCountPerTarget"
      resource_label         = "${var.alb_arn_suffix}/${var.target_group_arn_suffix}"
    }
    target_value       = 120
    scale_out_cooldown = 60
    scale_in_cooldown  = 300
  }
}