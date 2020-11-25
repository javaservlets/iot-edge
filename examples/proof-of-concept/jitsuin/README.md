### Jitsuin Integration Example

This example uses ForgeRock, Jistsuin and HiveMQ to demonstrate how to register, authenticate and authorize a device to
publishing data with the use of an OAuth 2.0 access token.

This example requires a running and configured AM server. Replace the `https://iot.iam.example.com/am` URL with your AM
server's URL.  

Start the HiveMQ Broker:
```
./run-broker.sh https://iot.iam.example.com/am
```

Run the device client:
```
./run-client.sh https://iot.iam.example.com/am
```

Stop the broker and client:
```
./stop-all.sh
```
