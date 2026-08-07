# lg-monteis

This repository is used for application development of the project monteis

## Documentation

The documentation for the project lives inside this repository.
For further information of the architecture consult the according [README.md](/documentation/architecture/README.md)

## Setup

To set up your local development environment, run:

```shell
./setup.sh
```

This configures Git to use the repository-provided `.githooks` directory for Git hooks.
This ensures consistent commit message enforcement and maintains a clean, traceable commit history across all contributors.

### Optional: local k8s manifest render gate

The `k8s/` tree includes `kustomize build` pre-commit hooks for fast local feedback.
Prerequisites: `kustomize` and `kubeconform` on your PATH.

After running `./setup.sh`, the hooks fire automatically on relevant `k8s/**/*.yaml` changes.
The authoritative blocker is the server-side `pr-gate.yml` job — local hooks are optional.

## Kubernetes Manifests (`k8s/`)

```
k8s/
  backend-base/          # Shared Kustomize base (Deployment, Service, SA, NP, CM, SecretStore)
  core/
    overlays/
      dev/               # Dev overlay
      staging/           # Scaffold
      prod/              # Scaffold
  pipeline/
    overlays/
      dev/               # Dev overlay
      staging/           # Scaffold
      prod/              # Scaffold
```

### Render gate

`.github/workflows/pr-gate.yml` includes a `manifest-render-gate` job that runs:

```bash
kustomize build k8s/core/overlays/dev | kubeconform -strict -ignore-missing-schemas
kustomize build k8s/pipeline/overlays/dev | kubeconform -strict -ignore-missing-schemas
```

This job runs in parallel with the Java build and blocks the PR merge on failure.

### CI digest-bump release step

After the Paketo build pushes the image to ECR, the CI pipeline bumps the overlay digest:

```bash
kustomize edit set image core=789130777196.dkr.ecr.eu-central-1.amazonaws.com/lg-monteis/core@sha256:<new-digest>
kustomize edit set image pipeline=789130777196.dkr.ecr.eu-central-1.amazonaws.com/lg-monteis/pipeline@sha256:<new-digest>
```

This commit to `main` triggers ArgoCD auto-sync for the dev overlay.

## Contribute

Follow the [CONTRIBUTING.md](CONTRIBUTING.md)
