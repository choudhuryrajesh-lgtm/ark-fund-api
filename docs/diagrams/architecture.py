#!/usr/bin/env python3
"""
Renders the AWS architecture diagram used in the README and
docs/03-System-Architecture.md.

Kept as code rather than a binary a diagramming tool exported, so the picture
is reviewable in a pull request and regenerating it after an infrastructure
change is a one-line command instead of "open the tool, find the file, export,
remember the same settings."

    pip install diagrams          # needs graphviz on PATH (brew install graphviz)
    python3 docs/diagrams/architecture.py

Writes docs/diagrams/aws-architecture.png.
"""

from pathlib import Path

from diagrams import Cluster, Diagram, Edge
from diagrams.aws.compute import ECS
from diagrams.aws.database import RDS
from diagrams.aws.management import Cloudwatch
from diagrams.aws.network import ALB, APIGateway, CloudFront, Route53
from diagrams.aws.security import SecretsManager
from diagrams.aws.storage import S3
from diagrams.onprem.client import Users
from diagrams.saas.logging import Newrelic

OUT = Path(__file__).with_name("aws-architecture")

graph_attr = {
    "fontsize": "15",
    "bgcolor": "transparent",
    "pad": "0.3",
    "splines": "spline",
    "nodesep": "0.5",
    "ranksep": "0.85",
}

node_attr = {"fontsize": "12"}

with Diagram(
    "Ark Fund API — AWS deployment",
    filename=str(OUT),
    outformat="png",
    show=False,
    direction="LR",
    graph_attr=graph_attr,
    node_attr=node_attr,
):
    users = Users("Fund admin /\nGP back office")

    with Cluster("Edge"):
        dns = Route53("Route 53\n(staging / production)")
        cdn = CloudFront("CloudFront")
        bucket = S3("S3\nReact UI")
        gw = APIGateway("API Gateway\nHTTP API")

    with Cluster("VPC — private subnets, 2 AZs"):
        alb = ALB("Internal ALB")

        with Cluster("ECS Fargate"):
            svc = ECS("ark-fund-api\nSpring Boot")

        db = RDS("RDS PostgreSQL\nMulti-AZ")

    with Cluster("Configuration & observability"):
        secrets = SecretsManager("Secrets Manager\nDB + agent credentials")
        logs = Cloudwatch("CloudWatch Logs")
        apm = Newrelic("New Relic APM")

        # Invisible chain so graphviz lays these left-to-right instead of
        # stacking them in one tall column, which otherwise doubles the
        # canvas height and leaves the left half of the image empty.
        secrets >> Edge(style="invis") >> logs >> Edge(style="invis") >> apm

    # Request path
    users >> Edge(label="HTTPS") >> dns >> gw
    users >> Edge(label="HTTPS") >> cdn
    cdn >> Edge(style="dotted", label="origin") >> bucket
    cdn >> Edge(label="/api/*") >> gw
    gw >> Edge(label="VPC Link") >> alb
    alb >> Edge(label="health-checked") >> svc
    svc >> Edge(label="TLS") >> db

    # Sidecar concerns
    svc >> Edge(style="dashed", color="darkgreen") >> secrets
    svc >> Edge(style="dashed", color="darkgreen") >> logs
    svc >> Edge(style="dashed", color="darkgreen", label="traces + logs") >> apm

print(f"wrote {OUT}.png")