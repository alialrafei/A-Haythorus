# A-Haythorus production Kubernetes template

This directory is a provider-neutral Kubernetes deployment template for running A-Haythorus as a sidecar alongside an application JVM.

## Required values

Replace `${...}` placeholders before deployment. At minimum define:

```text
NAMESPACE
APP_NAME
APP_INSTANCE
APP_CONTAINER_NAME
APP_DOCKER_IMAGE
APP_IMAGE_PULL_POLICY
APP_PORT
APP_REPLICAS
APP_SERVICE_ACCOUNT

APP_CPU_REQUEST
APP_CPU_LIMIT
APP_MEMORY_REQUEST
APP_MEMORY_LIMIT

AH_DOCKER_IMAGE
AH_IMAGE_PULL_POLICY
AH_PORT
AH_CPU_REQUEST
AH_CPU_LIMIT
AH_MEMORY_REQUEST
AH_MEMORY_LIMIT

AH_SERVICE_ACCOUNT
AH_ROLE_NAME
AH_ROLE_BINDING_NAME
AH_NETWORK_POLICY_NAME
AH_CLUSTER_CONFIG_NAME
AH_SERVICE_NAME
AH_SERVICE_PORT
AH_INGRESS_NAME
AH_HOST
AH_TLS_SECRET
INGRESS_CLASS_NAME

SHARDING_ENABLED
SHARD_COUNT
SHARD_ALGORITHM
SHARD_KEY_FIELDS
SHARD_LABEL
SHARD_MISSING_KEY_POLICY
MAX_CONCURRENT_REQUESTS
CONNECT_TIMEOUT_MS
REQUEST_TIMEOUT_MS

INGRESS_NAMESPACE_LABEL_KEY
INGRESS_NAMESPACE_LABEL_VALUE
KUBERNETES_API_NAMESPACE_LABEL_KEY
KUBERNETES_API_NAMESPACE_LABEL_VALUE
AUTH_ANNOTATION_KEY
AUTH_ANNOTATION_VALUE
```

## Shard key

The default production recommendation is to shard by stable application identity rather than Pod identity:

```text
namespace,label:app.kubernetes.io/name
```

Set the same `app.kubernetes.io/name` value on every replica of an application. Do not use the generated Pod name when replicas of one application must remain in the same shard.

Example:

```text
namespace=payments-prod
app.kubernetes.io/name=payments-api

payments-api-abc123  ─┐
payments-api-def456  ─┼─> same computed shard
payments-api-ghi789  ─┘
```

The `a-haythorus.io/shard` label is a materialized index produced by the existing A-Haythorus shard resolver; it is not intended to be manually assigned.

## Networking

The base deployment uses a `ClusterIP` Service and standard Kubernetes `Ingress`. The external authentication/SSO layer is deliberately platform-specific and should be configured at the ingress/authentication gateway. The repository does not require an application-specific Node.js server.

For OpenShift, the standard Kubernetes Ingress can be replaced with an OpenShift Route. For AWS/GCP/Azure/on-prem environments, use the ingress controller or Gateway implementation approved by that platform.

The A-Haythorus Service exposes only the sidecar HTTP port. The application's own Service/Ingress remains separate.

## Security

The sidecar uses a dedicated Pod ServiceAccount, namespace-scoped RBAC, NetworkPolicy, non-root execution, disabled privilege escalation, dropped Linux capabilities, and the RuntimeDefault seccomp profile.

`shareProcessNamespace: true` is intentional: it is required for process/JVM discovery and `/proc` inspection. It is therefore a deliberate relaxation of the normal container process isolation boundary. Do not add privileged mode or extra Linux capabilities merely to enable process monitoring.

The current self-managed shard-label materialization requires Pod `patch` permission. If shard labels are later materialized by an operator/controller, remove `patch` from the sidecar Role.

NetworkPolicy ingress/egress selectors are placeholders because the exact ingress-controller and Kubernetes API selectors depend on the target cluster/CNI. They must be reviewed before production deployment.

## Provider neutrality

The following resources are standard Kubernetes:

- ServiceAccount
- Role / RoleBinding
- ConfigMap
- NetworkPolicy
- Service
- Deployment
- Ingress

Only the external ingress/authentication implementation should vary by platform.
