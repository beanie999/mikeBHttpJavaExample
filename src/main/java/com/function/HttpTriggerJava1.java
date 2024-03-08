package com.function;

import java.util.*;
import java.net.*;
import javax.net.ssl.*;
import java.io.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import com.azure.messaging.servicebus.*;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import com.newrelic.api.agent.*;

/**
 * Azure Functions with HTTP Trigger.
 */
public class HttpTriggerJava1 {
    private static final String W3C_TRACE_PARENT = "traceparent";
    private static final String W3C_TRACE_STATE = "tracestate";
    private static final String NEW_RELIC_LOG_PATH = "/home/site/wwwroot/lib/logs/newrelic_agent.log";

    // Method to read the New Relic log file so it can be returned in the http call.
    private String readLogFile() {
        String rtn;
        try {
            Path logPath = Path.of(NEW_RELIC_LOG_PATH);
            rtn = Files.readString(logPath);
        }
        catch (IOException e) {
            rtn = e.getMessage();
        }
        return rtn;
    }

    // Trace this method in New Relic
    @Trace ()
    private List<ServiceBusMessage> createMessages(final ExecutionContext context)
    {
        // Get the trace headers so we can add them to the message as application properties.
        // ConcurrentHashMapHeaders provides a concrete implementation of com.newrelic.api.agent.Headers
        Headers distributedTraceHeaders = ConcurrentHashMapHeaders.build(HeaderType.MESSAGE);
        // Generate W3C Trace Context headers and insert them into the distributedTraceHeaders map
        NewRelic.getAgent().getTransaction().insertDistributedTraceHeaders(distributedTraceHeaders);

        // Create a random number of messages.
        Random rand = new Random(); 
        int n = rand.nextInt(5);
        n++;

        // create a list of messages and return it to the caller
        ServiceBusMessage[] messages = new ServiceBusMessage[n];
        for (int i = 0; i < n; i++) {
            messages[i] = new ServiceBusMessage("Message: " + i);
            // Add the trace headers as application properties.
            if (distributedTraceHeaders.containsHeader(W3C_TRACE_PARENT)) {
                messages[i].getApplicationProperties().put(W3C_TRACE_PARENT, distributedTraceHeaders.getHeader(W3C_TRACE_PARENT));
            }
            if (distributedTraceHeaders.containsHeader(W3C_TRACE_STATE)) {
                messages[i].getApplicationProperties().put(W3C_TRACE_STATE, distributedTraceHeaders.getHeader(W3C_TRACE_STATE));
            }
        }
        context.getLogger().info("Number of messages created: " + n);

        return Arrays.asList(messages);
    }

    // Trace this method in New Relic
    @Trace ()
    private void sendMessageBatch(final ExecutionContext context)
    {
        String connectionString = System.getenv("SERVICE_BUS_CONNECTION");
        String queueName = System.getenv("SERVICE_BUS_QUEUE");

        // create a Service Bus Sender client for the queue
        ServiceBusSenderClient senderClient = new ServiceBusClientBuilder()
            .connectionString(connectionString)
            .sender()
            .queueName(queueName)
            .buildClient();

        // Creates an ServiceBusMessageBatch where the ServiceBus.
        ServiceBusMessageBatch messageBatch = senderClient.createMessageBatch();

        // create a list of messages
        List<ServiceBusMessage> listOfMessages = createMessages(context);

        // We try to add as many messages as a batch can fit based on the maximum size and send to Service Bus when
        // the batch can hold no more messages. Create a new batch for next set of messages and repeat until all
        // messages are sent.
        for (ServiceBusMessage message : listOfMessages) {
            if (!messageBatch.tryAddMessage(message)) {
                // The batch is full, so we create a new batch and send the batch.
                senderClient.sendMessages(messageBatch);
                context.getLogger().info("Sent a batch of messages to the queue: " + queueName);

                // create a new batch
                messageBatch = senderClient.createMessageBatch();

                // Add that message that we couldn't before.
                if (!messageBatch.tryAddMessage(message)) {
                    context.getLogger().warning("Message is too large for an empty batch. Skipping. Max size: " + messageBatch.getMaxSizeInBytes());
                }
            }
        }

        if (messageBatch.getCount() > 0) {
            senderClient.sendMessages(messageBatch);
            context.getLogger().info("Sent a batch of messages to the queue: " + queueName);
        }

        //close the client
        senderClient.close();
    }

    // Trace this method in New Relic
    @Trace ()
    static void sendMessage(final ExecutionContext context)
    {
        String connectionString = System.getenv("SERVICE_BUS_CONNECTION");
        String queueName = System.getenv("SERVICE_BUS_QUEUE");
        NewRelic.addCustomParameter("queueName", queueName);
        // create a Service Bus Sender client for the queue
        ServiceBusSenderClient senderClient = new ServiceBusClientBuilder()
            .connectionString(connectionString)
            .sender()
            .queueName(queueName)
            .buildClient();

        // send one message to the queue
        senderClient.sendMessage(new ServiceBusMessage("Hello, World!"));
        context.getLogger().info("Sent a single message to the queue: " + queueName);
    }

    // Trace this method in New Relic
    @Trace ()
    // Method to call another Azure Function via http.
    private void callAppFunc(final ExecutionContext context) {
        try {
            String stringURL = System.getenv("URL_TO_CALL");
            URL url = new URL(stringURL);

            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            // Send get request
            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.flush();
            wr.close();

            int responseCode = con.getResponseCode();
            context.getLogger().info("Response code is: " + responseCode);

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            context.getLogger().info("Response Text: " + response.toString());
        }
        catch (Exception e) {
            context.getLogger().warning(e.getMessage());
        }
    }

    /**
     * This function listens at endpoint "/api/HttpTriggerJava1". Two ways to invoke it using "curl" command in bash:
     * 1. curl -d "HTTP Body" {your host}/api/HttpTriggerJava1
     * 2. curl {your host}/api/HttpTriggerJava1?name=HTTP%20Query
     */
    // Start a new transaction to be traced in New Relic.
    @Trace (dispatcher=true)
    @FunctionName("HttpTriggerJava1")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Parse query parameter
        String query = request.getQueryParameters().get("name");
        String name = request.getBody().orElse(query);

        if (name == null) {
            context.getLogger().warning("No name supplied!");
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Please pass a name on the query string or in the request body").build();
        } else {
            // Set the user id in New Relic (it will be used by Errors Inbox)
            NewRelic.setUserId(name);
            context.getLogger().info("Name is: " + name);
            // Call another Azure Function via http
            this.callAppFunc(context);
            // Send some messages to the Service Bus
            this.sendMessageBatch(context);
            return request.createResponseBuilder(HttpStatus.OK).body("Hello there, " + name + "\n" + readLogFile()).build();
        }
    }
}
