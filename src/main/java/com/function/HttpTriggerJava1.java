package com.function;

import java.util.*;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.NewRelic;
import java.net.*;
import javax.net.ssl.*;
import java.io.*;

/**
 * Azure Functions with HTTP Trigger.
 */
public class HttpTriggerJava1 {
    @Trace ()
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
            context.getLogger().info(new String("Response code is: ") + responseCode);

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            context.getLogger().info(new String("Response Text: ") + response.toString());
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
    @Trace (dispatcher=true)
    @FunctionName("HttpTriggerJava1")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Parse query parameter
        String query = request.getQueryParameters().get("name");
        String name = request.getBody().orElse(query);

        //NewRelic.setRequestAndResponse(null, null);
        if (name == null) {
            context.getLogger().warning("No name supplied!");
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Please pass a name on the query string or in the request body").build();
        } else {
            NewRelic.addCustomParameter("userName", name);
            context.getLogger().info(new String("Name is: ") + name);
            this.callAppFunc(context);
            return request.createResponseBuilder(HttpStatus.OK).body("Hello there, " + name).build();
        }
    }
}
