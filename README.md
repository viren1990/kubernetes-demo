# A sample set up to test app deployment on kubernetes.

Salient features of app are as follows ;

- Three functioning apps (**customers** , **tracking-service** & **orders**)
- In all the apps,data persisted thus powered by embedded H2 database.
- Business and data view in both the apps are kept minimal since the motive here is to validate their 
  deployments on kubernetes.
- **Tracking** & **orders** apps function as dependent apps on **customers** as in a business sense, customer's 
  orders and in turn order's tracking are 
  enquired from **orders** & **tracking** services (HTTP client is exercised in customers & orders app).

## How to interact with apps.

- Prerequisites : Java17 , docker, curl, docker-compose, colima/docker desktop & jq.
- Clone the repository on local machine.
```html
$ cd <path_to_project_dir>/kubernetes-demo
$ docker-compose up  #first time it'll take a few minutes to finish.
```
- Validate 3 containers should come up.
```html
$ docker containers ls

CONTAINER ID   IMAGE                               COMMAND                  CREATED          STATUS          PORTS                                                 NAMES
21810a8b775c   kubernetes-demo-customers-service   "java org.springfram…"   18 minutes ago   Up 18 minutes   0.0.0.0:8080->8080/tcp, :::8080->8080/tcp             kubernetes-demo-customers-service-1
a91b80c1d1fe   kubernetes-demo-orders-service      "java org.springfram…"   18 minutes ago   Up 18 minutes   8080/tcp, 0.0.0.0:9001->9001/tcp, :::9001->9001/tcp   kubernetes-demo-orders-service-1
274a60f26e0c   kubernetes-demo-tracking-service    "java org.springfram…"   18 minutes ago   Up 18 minutes   8080/tcp, 0.0.0.0:9002->9002/tcp, :::9002->9002/tcp   kubernetes-demo-tracking-service-1
```

- Validate success for a few curls
```html
$ curl -X GET http://localhost:8080/customers/4 | jq
```
```json
{
  "customer": {
    "id": 4,
    "name": "Hari"
  },
  "orders": [{
    "orderId": 4,
    "productName": "250g, Bru Coffee",
    "tracking": {
      "partner": "FEDEX",
      "status": "DELIVERED",
      "trackingId": 5
    }
  },
    {
      "orderId": 5,
      "productName": "500g, Nirma Detergent",
      "tracking": {
        "partner": "FEDEX",
        "status": "DISPATCHED",
        "trackingId": 1,
        "tentativeDeliveryDate": "2023-05-19"
      }
    }
  ]
}
```
```html
$ curl -X GET http://localhost:8080/customers | jq
```
```json
[{
"id": 1,
"name": "Aakash"
},
{
"id": 2,
"name": "Viren"
},
{
"id": 3,
"name": "Anusha"
},
{
"id": 4,
"name": "Hari"
},
{
"id": 5,
"name": "Suriya"
},
{
"id": 6,
"name": "Sajeev"
},
{
"id": 7,
"name": "Sameer"
}
]
```
```html
$ curl -X GET http://localhost:9001/ | jq
```
```json
[{
"id": 1,
"customerId": 1,
"productName": "10kg Fortune Wheat Flour"
},
{
"id": 2,
"customerId": 2,
"productName": "200g Emami Bath Soap"
},
{
"id": 3,
"customerId": 3,
"productName": "2kg Safeda Mango"
},
{
"id": 4,
"customerId": 4,
"productName": "250g, Bru Coffee"
},
{
"id": 5,
"customerId": 4,
"productName": "500g, Nirma Detergent"
},
{
"id": 6,
"customerId": 5,
"productName": "250g Tata Tea"
},
{
"id": 7,
"customerId": 6,
"productName": "1kg Toor Dal"
},
{
"id": 8,
"customerId": 7,
"productName": "1/2kg Carrot"
}
]
```

- Manufacture delay
```html
$ curl --location 'http://localhost:8080/customers/4?emulateDelay=yes&delayInMs=5000' | jq
```
```json
% Total % Received % Xferd Average Speed Time Time Time Current Dload Speed Upload Total Spent Left Speed
100 317 100 317 0 0 59 0 0: 00: 05 0: 00: 05--: --: --102 {
"customer": {
"id": 4,
"name": "Hari"
},
"orders": [{
"orderId": 4,
"productName": "250g, Bru Coffee",
"tracking": {
"partner": "FEDEX",
"status": "DISPATCHED",
"trackingId": 1,
"tentativeDeliveryDate": "2023-05-19"
}
},
{
"orderId": 5,
"productName": "500g, Nirma Detergent",
"tracking": {
"partner": "FEDEX",
"status": "DELIVERED",
"trackingId": 5
}
}
]
}
```
- Manufacture failure
```html
curl --location 'http://localhost:8080/customers/8?
emulateFailure=yes&failureHttpCode=502&customizeBehaviorTargetApp=<app_name>' | jq

app name values are ['customers', 'orders', 'tracking']
```
```json
% Total    % Received % Xferd  Average Speed   Time    Time     Time  CurrentDload  Upload   Total   Spent    Left  Speed
100   585  100   585    0     0    475      0  0:00:01  0:00:01 --:--:--   478
{
"timestamp": "2023-05-19T17:19:52.487+00:00",
"path": "/customers/4",
"status": 500,
"error": "Internal Server Error",
"message": "{\"timestamp\":\"2023-05-19T17:19:52.348+00:00\",\"path\":\"/customer-orders/4\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"{\\\"timestamp\\\":\\\"2023-05-19T17:19:51.735+00:00\\\",\\\"path\\\":\\\"/order-tracking/4\\\",\\\"status\\\":500,\\\"error\\\":\\\"Internal Server Error\\\",\\\"message\\\":\\\"failed on purpose at tracking app.\\\",\\\"requestId\\\":\\\"37ab5aed-11\\\"}\",\"requestId\":\"a7852866-8\"}",
"requestId": "20ece88b-10"
}

```
## Kubernetes Deployment

- On local - Prerequisites: minikube, kubectl
- Up minikube kubernetes cluster on your local
- Verify minikube status 
```html
$ minikube status

minikube
type: Control Plane
host: Running
kubelet: Running
apiserver: Running
kubeconfig: Configured
```
- From project's root , run ;
``$ kubectl apply -f kube``
- Post successful response from above command, validate pods
```html
$ kubectl get pods -o wide

NAME                             READY   STATUS    RESTARTS   AGE    IP            NODE       NOMINATED NODE   READINESS GATES
customers-5ddb75b6f6-qxqng       2/2     Running   0          34m    10.244.0.17   minikube     <none>           <none>
orders-75f7bd96d6-jxsh8          2/2     Running   0          34m    10.244.0.18   minikube     <none>           <none>
tracking-service-5f564d6f77-fst7h   2/2     Running   2 (29m ago)   48m   10.244.0.55   minikube   <none>           <none>
```
- Validate service instances for customers, tracking & orders pods.
```html
$ kubectl get svc
NAME          TYPE           CLUSTER-IP       EXTERNAL-IP     PORT(S)        AGE
customers     LoadBalancer   10.98.243.74     192.168.64.11   80:31522/TCP   38m
orders        ClusterIP      10.100.59.251    <none>          9001/TCP       38m 
tracking-service   ClusterIP      10.105.154.31    <none>          9002/TCP       49m
```
- Mind the service type for customers service, A LoadBalancer type.
- Get the URL for customers service.
```html
$ minikube service customers --url

http://192.168.64.2:31522
```
- Verify the customers service health by accessing http://192.168.64.2:31522/actuator/health
- Validate aforementioned customers APIs results. 
- Since tracking & orders services are not exposed to the public ,therefore remain inaccessible.
