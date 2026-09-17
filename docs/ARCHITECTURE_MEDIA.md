# Architecture media

The README architecture-media block is:

```markdown
## Architecture at a glance

The architecture diagram below shows the main A-Haythorus deployment model: one sidecar attached to the target JVM, Linux process telemetry through `/proc`, Kubernetes API-based peer discovery, and bounded peer HTTP aggregation without a central push collector.

### Static architecture

![A-Haythorus Infrastructure Architecture](docs/assets/architecture.svg)

### Animated architecture walkthrough

![A-Haythorus Animated Architecture Walkthrough](docs/assets/architecture-animated.svg)

The animated version highlights the main telemetry and peer-discovery flows.
```

The static diagram is stored at `docs/assets/architecture.svg` and the animated walkthrough is stored at `docs/assets/architecture-animated.svg`.

The animated asset is an SVG walkthrough rather than the original MP4 because the available repository integration can create text files but cannot upload binary repository assets. The supplied architecture video was used as the visual reference for the animated flow.
