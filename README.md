# Example Java Azure Function instrumented by New Relic
This project provides an example Java Azure Function instrumented by New Relic, which calls another Azure Function via http and sends messages to a Service Bus.

## How it works
The Function calls another Azure Function via http (example [repo](https://github.com/beanie999/mikeBHttpDotNetExample)) and sends messages to a Servce Bus
including the trace headers under application properties. The trace headers can be consumed by the function receiving the messages (see this [repo](https://github.com/beanie999/mikeBServiceBusDNExample)).

## Setup
- Ensure Application Insights is switched off.
- Add the `newrelic-agent.jar` and `newrelic-api.jar` files to the project.

Add the following configuration settings:
- `NEW_RELIC_LICENSE_KEY` and optionally `NEW_RELIC_APP_NAME`.
- `JAVA_OPTS` or `languageWorkers__java__arguments` depending on your plan (see [here](https://learn.microsoft.com/en-us/azure/azure-functions/functions-reference-java?tabs=bash%2Cconsumption#customize-jvm)), adding the `newrelic-agent.jar` file (for example `-javaagent:/home/site/wwwroot/lib/newrelic-agent-8.9.1.jar`).

Ensure the newrelic.yml file is included in your project and set the following values:
- Optionally `app_name`, depending if it has been included as a configuration setting.
- Send data on exit parameters (under the `common` block):
```yml
    sync_startup: true 
    send_data_on_exit: true
    send_data_on_exit_threshold: 0.001
    sampling_target: 100
```    
- Exclude the following instrumentations (under the `class_transformer` block):
```yml
    com.newrelic.instrumentation.netty-3.4:
      enabled: false
    com.newrelic.instrumentation.netty-3.8:
      enabled: false
    com.newrelic.instrumentation.netty-4.0.0:
      enabled: false
    com.newrelic.instrumentation.netty-4.0.8:
      enabled: false
    com.newrelic.instrumentation.netty-reactor-0.9.0:
      enabled: false
    com.newrelic.instrumentation.netty-reactor-0.8.0:
      enabled: false
    com.newrelic.instrumentation.netty-reactor-0.7.0:
      enabled: false
```

Add code for annotation and custom attributes:
- Annotate the main method so it appears as a transaction in New Relic `@Trace (dispatcher=true)`.
- Annotate other methods as required `@Trace ()`.
- Add trace headers to the messages (see `createMessages` method in the `HttpTriggerJava1` class).
- Add custom attributes as required.

Optionally instrument other methods via XML:
- Create an XML file (see `new-relic-custom.xml` for an example).
- Ensure the XML file is included in the build.
- Add the XML file folder location to the `newrelic.yml` file as the `extensions.dir` parameter under the `common` block.
