# Deployment of service as container on Platform

In this tutorial, we will see how to deploy the relation extraction service provided with this tool using container runtime on to the digitalhub platform.


## 1. Initialize the project

```python
import digitalhub as dh
PROJECT_NAME = "redit"
proj = dh.get_or_create_project(PROJECT_NAME)
```


## 2. Register the `redit` operation in the project

```python
function = proj.new_function(
    "redit",
    kind="container",
    image="ghcr.io/tn-aixpa/redit:1.1.0"
)
```
<p align="justify">The function represent a container runtime that allows you to deploy services on Kubernetes. It uses the image of redit container deploved in the context of project, create the environment required for the execution. The image on launch starts the the relation extraction service which takes as input sentences which are used to depict relation extraction. The service predict attributes and relations for entities in a sentence.
</p>

## 3. Run

The next step is to serve the function which will deploy the container function 'redit' as a long-lived service. The service is started inside to the container on port 8015 (target_port) while host port is specified with parameter (port). The 'resources' parameter has been configured as per the requirement of the service.

```python
serve_run = function.run(
     action="serve",
     service_ports=[{"port": 8015, "target_port": 8015}],
     resources={"cpu": {"requests": "4", "limits": "8"}, "mem":{"requests": "4Gi", "limits": "8Gi"}},
     wait=True
)
```
As a result of this operation the service 'redit' starts on host port 8015.

Note: For more details about container runtime and its operation, please consult the platform<a href="https://scc-digitalhub.github.io/sdk-docs/reference/runtimes/container/overview/">documentation</a>


## 4. Test the API
Once deployed, we can test the API. Using 'Requests' HTTP library for Python make a POST request the the service as shown below. 

```python
import requests

SERVICE_URL = serve_run.refresh().status.to_dict()["service"]["url"]

with requests.post(f'http://{SERVICE_URL}/tint', data="Il sottoscritto Luca Rosetti, nato a Brindisi il 4 maggio 1984 e residente a Sanremo (IM) in Via Matteotti 42 dichiara di essere titolare dell") as r:
    res = r.content
print(res)
```
Note: Inside the project 'src' folder there exist a jypter notebook <a href="../src/deploy.ipynb">deploy.ipynb</a> that depicts the service deployment and testing operations.
