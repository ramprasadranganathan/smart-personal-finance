# Deploying Angular App to GKE with ArgoCD

This guide explains how to deploy the `web-angular` application to Google Kubernetes Engine (GKE) using the created Helm chart and ArgoCD.

## Prerequisites

1.  **Google Cloud SDK (`gcloud`)**: For interacting with GKE.
2.  **`kubectl`**: For Kubernetes command-line access.
3.  **`helm`**: For chart management (optional if using ArgoCD, but good for local testing).
4.  **`docker`**: Understood to be present for building images.

## 1. Interaction with GKE

To interact with your GKE cluster, you first need to authenticate and get the credentials.

### Login to Google Cloud
```bash
gcloud auth login
```

### Set Project and Zone/Region
```bash
gcloud config set project [YOUR_PROJECT_ID]
gcloud config set compute/region [YOUR_REGION] # or compute/zone
```

### Get Credentials
This command simplifies `kubectl` configuration by fetching credentials and updating your `kubeconfig` file.
```bash
gcloud container clusters get-credentials [CLUSTER_NAME] --region [YOUR_REGION]
```

### Verify Connection
```bash
kubectl get nodes
```

## 2. Docker Image Creation and Push

Before deploying, you must build and push the Docker image to a container registry (e.g., Google Artifact Registry or Docker Hub).

```bash
# Authenticate docker with gcloud (if using GCR/GAR)
gcloud auth configure-docker

# Option A: Build with local Docker
# docker build -t gcr.io/[YOUR_PROJECT_ID]/web-angular:v1 apps/web-angular
# docker push gcr.io/[YOUR_PROJECT_ID]/web-angular:v1

# Option B: Build with Cloud Build (No local Docker required)
# This sends the build context to Google Cloud and builds the image there.
# Note: You need the 'Cloud Build Editor' role.
# Command to grant: gcloud projects add-iam-policy-binding [YOUR_PROJECT_ID] --member="user:[YOUR_EMAIL]" --role="roles/cloudbuild.builds.editor"
gcloud builds submit --tag gcr.io/[YOUR_PROJECT_ID]/web-angular:v1 apps/web-angular
```

> **Note**: Update `charts/web-angular/values.yaml` with your actual image repository and tag.

## 3. Install ArgoCD (If not already installed)

If ArgoCD is not running in your cluster:

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

Access the UI (Port Forwarding):
```bash
kubectl port-forward svc/argocd-server -n argocd 8080:443
```
Open `https://localhost:8080`.
Username: `admin`
Password:

**Bash (Linux/Mac/Git Bash):**
```bash
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d; echo
```

**PowerShell (Windows):**
```powershell
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | ForEach-Object { [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($_)) }
```

## 4. Deploy Application via ArgoCD

Apply the application manifest created in `infra/argocd/web-angular-app.yaml`. This tells ArgoCD to monitor your git repository and deploy the Helm chart.

```bash
kubectl apply -f infra/argocd/web-angular-app.yaml
```

Check the status in ArgoCD UI or via `kubectl`:
```bash
kubectl get application -n argocd
```

## 5. Verify Deployment

Once ArgoCD syncs the application:

```bash
kubectl get pods -n web-angular
kubectl get svc -n web-angular
```

If you enabled Ingress in `values.yaml`, check the ingress IP:
```bash
kubectl get ingress -n web-angular
```

## Troubleshooting

-   **ImagePullBackOff**: Check if the image path in `values.yaml` is correct and the GKE cluster has permissions to pull from the registry.
-   **ArgoCD Sync Failed**: Check if the repo URL in `infra/argocd/web-angular-app.yaml` is correct including `.git` extension and if the path `charts/web-angular` exists in the branch.

### Private Repository Authentication
If your repository is **private**, ArgoCD needs credentials to access it. You see errors like `authentication required`.

**Option 1: Add via UI**
1.  Go to **Settings** > **Repositories**.
2.  Click **Connect Repo**.
3.  Select **VIA HTTPS**.
4.  Enter Repo URL, Username, and **Password/PAT** (Personal Access Token).
    *   **Important**: Ensure your PAT has the **`repo`** scope (Full control of private repositories). If using Fine-Grained tokens, ensure it has "Read-only" access to "Contents" of this specific repository.

**Option 2: Add via Kubernetes Secret (Declarative)**
Create a file `repo-secret.yaml` (don't commit this if it has real credentials, or use a secret manager):

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: private-repo-creds
  namespace: argocd
  labels:
    argocd.argoproj.io/secret-type: repository
stringData:
  type: git
  url: https://github.com/ramprasadranganathan/smart-personal-finance.git
  username: [YOUR_GITHUB_USERNAME]
  password: [YOUR_GITHUB_PAT]
```
Apply it: `kubectl apply -f repo-secret.yaml`
