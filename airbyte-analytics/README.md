# airbyte-analytics

This module contains the logic for analytics tracking.

## Supported Analytics Trackers

* Logging/Standard Out
* [Segment](https://segment.com/)

## Configuration

The analytics tracker is initialized automatically based on the current service configuration.  The type of the
analytics tracker can be configured by setting the `TRACKING_STRATEGY` environment variable.  Valid options
for this environment variable include:

* `LOGGING`
* `SEGMENT`

If the `SEGMENT` tracking strategy type is enabled, the write key must also be provided using the `SEGMENT_WRITE_KEY` 
environment variable. In addition to the type of analytics tracker, the following environment variables must also be set:

* `AIRBYTE_ROLE`
* `AIRBYTE_EDITION`
* `AIRBYTE_VERSION`

## Development

In order to use this module in a service at runtime,  add the following configuration to the service's `application.yml` file:

```yaml
micronaut:
  caches:
    # used by the analytics tracking client to cache calls to resolve the deployment and identity (workspace) for
    # track events
    analytics-tracking-deployments:
      charset: "UTF-8"
      expire-after-access: 10m
    analytics-tracking-identity:
      charset: "UTF-8"
      expire-after-access: 10m
      
airbyte:
  edition: ${AIRBYTE_EDITION:COMMUNITY}
  role: ${AIRBYTE_ROLE:dev}
  tracking:
    strategy: ${TRACKING_STRATEGY:LOGGING}
    write-key: ${SEGMENT_WRITE_KEY:}
  version: ${AIRBYTE_VERSION:dev}
```

Finally, you may also declare overrides of the following singleton beans to fetch information at runtime:

```kotlin
@Singleton
@Named("deploymentSupplier")
@Replaces(named="deploymentSupplier")
fun deploymentSupplier(deploymentMetadataApi: DeploymentMetadataApi) {
    return Supplier { deploymentMetadataApi.getDeploymentMetadata() }
}

@Singleton
@Named("workspaceFetcher")
@Replaces(named="workspaceFetcher")
fun workspaceFetcher(workspaceApi: WorkspaceApi): Function<UUID, WorkspaceRead> {
    return Function { workspaceId:UUID -> workspaceApi.getWorkspace(WorkspaceIdRequestBody().workspaceId(workspaceId).includeTombstone(true)) }
}

```

Note that the configuration above is an example and may need to be modified to fit the including service.