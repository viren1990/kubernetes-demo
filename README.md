# A sample set up to test app deployment on kubernetes.

Salient features of app are as follows ;

- Two functioning apps (**customers** & **orders**)
- In both the apps,data persisted thus powered by embedded H2 database.
- Business and data view in both the apps are kept minimal since the motive here is to validate their 
  deployments on kubernetes.
- **orders** app functions as a dependent app on **customers** as in a business sense, customer's orders are 
  enquired from orders service (HTTP client is exercised in customers app).

## How to interact with apps.

- Prerequisites : Java17 , docker, curl, docker-compose, colima/docker desktop & jq.
- Clone the repository on local machine.
```html
$ cd <path_to_project_dir>/kubernetes-demo
$ docker-compose up  #first time it'll take a few minutes to finish.
```
- Validate 2 containers should come up.
```html
$ docker containers ls
```
![img.png](images/img.png)
- Validate success for a few curls
```html
$ curl -X GET http://localhost:8080/customers/4 | jq
```
![img_1.png](images/img_1.png)
```html
$ curl -X GET http://localhost:8080/customers | jq
```
![img_2.png](images/img_2.png)
```html
$ curl -X GET http://localhost:9001/ | jq
```
![img.png](images/img_3.png)
