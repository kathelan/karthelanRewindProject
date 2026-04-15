# OpenShift Developer Sandbox — deployment guide

Namespace: `kathelan-dev`
Registry: `quay.io/kathelan`

## Wymagania

- [oc CLI](https://docs.openshift.com/container-platform/latest/cli_reference/openshift_cli/getting-started-cli.html): `brew install openshift-cli`
- Docker Desktop
- Konto Red Hat + quay.io (ten sam login)

## 1. Logowanie

### quay.io
```bash
docker login quay.io
```

### OpenShift (token z konsoli webowej → góra prawo → "Copy login command")
```bash
oc login --token=<token> --server=https://api.<sandbox>.openshiftapps.com:6443
```

## 2. Build i push obrazów

Buduj zawsze z `--platform linux/amd64` (OpenShift działa na AMD64, Mac na ARM64).
Uruchamiaj z **głównego katalogu projektu**.

```bash
docker build --platform linux/amd64 -f soap-service/Dockerfile \
  -t quay.io/kathelan/kathelan-soap-service:latest .
docker push quay.io/kathelan/kathelan-soap-service:latest

docker build --platform linux/amd64 -f user-service/Dockerfile \
  -t quay.io/kathelan/kathelan-user-service:latest .
docker push quay.io/kathelan/kathelan-user-service:latest

docker build --platform linux/amd64 -f auth-service/Dockerfile \
  -t quay.io/kathelan/kathelan-auth-service:latest .
docker push quay.io/kathelan/kathelan-auth-service:latest
```

## 3. Deploy na OpenShift

```bash
oc apply -f openshift/secret.yml
oc apply -f openshift/soap-service.yml
oc apply -f openshift/user-service.yml
oc apply -f openshift/auth-service.yml
```

## 4. Weryfikacja

```bash
# Status podów
oc get pods -n kathelan-dev

# Publiczne URLe
oc get routes -n kathelan-dev

# Logi serwisu
oc logs deployment/soap-service -n kathelan-dev
oc logs deployment/user-service -n kathelan-dev
oc logs deployment/auth-service -n kathelan-dev
```

## 5. Aktualizacja po zmianie kodu

```bash
# Rebuild + push (np. soap-service)
docker build --platform linux/amd64 -f soap-service/Dockerfile \
  -t quay.io/kathelan/kathelan-soap-service:latest .
docker push quay.io/kathelan/kathelan-soap-service:latest

# Restart deploymentu
oc rollout restart deployment/soap-service -n kathelan-dev

# Sprawdź status rollout
oc rollout status deployment/soap-service -n kathelan-dev
```

## Publiczne URLe (sandbox)

- user-service: `https://user-service-kathelan-dev.apps.rm1.0a51.p1.openshiftapps.com`
- auth-service: `https://auth-service-kathelan-dev.apps.rm1.0a51.p1.openshiftapps.com`
- soap-service: tylko wewnętrzny (`http://soap-service:8080/ws`)

## Przykładowe requesty

```bash
# Stwórz użytkownika
curl -X POST "https://user-service-kathelan-dev.apps.rm1.0a51.p1.openshiftapps.com/users" \
  -H "Content-Type: application/json" \
  -d '{"name":"Jan Kowalski","city":"Warsaw","email":"jan@test.com"}'

# Pobierz użytkowników z miasta
curl "https://user-service-kathelan-dev.apps.rm1.0a51.p1.openshiftapps.com/users?city=Warsaw"

# Pobierz użytkownika po id
curl "https://user-service-kathelan-dev.apps.rm1.0a51.p1.openshiftapps.com/users/<id>"
```

## Struktura plików

```
openshift/
├── secret.yml        # SOAP credentials (username/password)
├── soap-service.yml  # Deployment + Service (port 8080, internal only)
├── user-service.yml  # Deployment + Service + Route (port 8081)
└── auth-service.yml  # Deployment + Service + Route (port 8082)
```

## Znane problemy i rozwiązania

| Problem | Przyczyna | Rozwiązanie |
|---|---|---|
| `no image found for architecture amd64` | Obraz zbudowany na ARM64 (Apple Silicon) | Dodaj `--platform linux/amd64` do `docker build` |
| `no main manifest attribute` | `spring-boot-maven-plugin` z `<classifier>exec</classifier>` generuje dwa JARy | Dockerfile kopiuje `*-exec.jar` zamiast `*.jar` |
| Brakujące pom.xml w Docker build | Root `pom.xml` deklaruje wszystkie moduły — Maven potrzebuje ich pom-ów | Każdy Dockerfile kopiuje pom.xml wszystkich modułów |
