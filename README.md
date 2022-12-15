# Hacking Hate Speech

This repository contains source code for several google cloud functions as well as a workflow which orchestrates them. These
all come together to form an AI powered sexist speech detector in slack.

## Running a function locally

You can run cloud functions locally using the `runFunction` task for its respective gradle subproject. Example commands
you could run are:

 - `./gradlew SlackMessageExtractor:runFunction`

The function should output the port it's running on in the logs, and then you can send HTTP requests to the running server.
These requests are HTTP POST messages containing some JSON.

For example, you could send this message to the slack message extractor:

```http request
POST localhost:8080
Content-Type: application/json

{
  "channel": "C12345678"
}
```

## Building and deploying a function

The cloud functions' source code can be built with the following command:

```shell
./gradlew buildFunction
```

This will output staging folders for each function which can then be deployed to Google Cloud with 
`gcloud functions deploy FUNCTION-NAME --source ./STAGING-FOLDER --gen2 --trigger-http`. The staging folders
have the naming convention `deploy-MODULE_NAME`.

## Deploying the cloud workflow

The cloud workflow described in [HackingHateWorkflow.yaml](./HackingHateWorkflow.yaml) can be deployed with the
google cloud CLI: `gcloud workflows deploy WORKFLOW-NAME --source ./HackingHateWorkflow.yaml`