
# Measurement broadcasting using gRPC

It is an app that takes advantage of an opportunity that HTTP/2 provides - 
streaming, particularly bidirectional streaming. With a help of gRPC 
framework besides processing standard unary calls, a server is able to
perform server streaming, listen to a client streaming and eventually combine both of them 
into a bidirectional streaming.

To study this feature I used a setup of:
- sensors that publish some kind of measurements
- clients that can choose specific sensors and subscribe on them

## Tech Stack

**Server:** Java Core, gRPC, RabbitMQ

## Demo

A schema of how it works.
[schema](./schema.png)

A .gif demonstrates how data is produced and consumed.
[demo.gif](./demo.gif)
#### Other features
- Sensor and Client registration and authentication (*JWT is used*)
- Listing sensors and their Online Status
- Requesting measurement history for a Sensor

# Lessons Learned and Challenges

### Lessons
- **What is protobuf?**
*It is a set of instruments and descriptions of objects that later will be serialized/deserialized into/from bytes.*
- **What is gRPC?**
*A framework that uses protobuf as a way to transfer data via network and remote procedure call
for interraction with a remote resource.*
- **Is there no streaming in HTTP/1.1? And if there is one, how is it different from HTTP/2's?**
*Sure there is... and it is all right if a server provides some static data
that is not frequently changed depending on another request. So what happens 
if a client has an opportunity to change the flow of data server provides? 
A client has to make a new HTTP request, open another TCP connection, then a server
somehow has to bind the request and streaming that has already started 
together, process the response, modify the stream... every time... Uff, too complicated... 
HTTP/2 makes the life much easier and allows to transfer data from one side to another in a single connection at the same time.*

## Challenges
### Non-blocking measurement publishing

The main problem I had to resolve is to define what kind of exchange should I place between
sensors (producers) and subscribers (consumers). It could be the observer pattern, 
the subscriber/publisher pattern or a subscriber could be more self-contained 
and perform long polling with a given timeout. So I've chosen the subscriber/publisher 
option and RabbitMQ as a tool to achieve it.

Btw the benefit subscriber/publisher pattern offers whereas observer pattern does not is the fact that it does 
not block publisher's thread. In conditions when
there is a huge mass of subscribers that have to be notified it becomes an important issue.

### Subscription management

A client is able to modify the list of sensors he subscribed on 
using the same connection on which he consumes data. Within only one connection 
opened not only the server provides data for a client he once required, but a client as well 
is able to talk with server and change his preferences. That is what a bidirectional
streaming allows us to do.


## Run Locally

Supposing there is a RabbitMQ instance already running on a computer, 
then from project's root directory build  an image
```
docker build -t grpc-sensors-server .
```
And tell the container that the host with name `lh.internal` 
(can be used any other name) is the actual localhost where RabbitMQ is running on.
The server would like to know how to address to the RMQ and waits for the `-mqh` argument
```
docker run --add-host lh.internal:host-gateway -p 8090:8090 grpc-sensors-server -p 8090 -mqh lh.internal
```
**Yet it is easier to run both server and RabbitMQ with docker compose**. From the project's root directory
```
docker compose up --build
```
Starts server at the `8090` port by default.


## Usage/Examples

I highly recommend Postman to perform gRPC calls. The protobuf file it requires 
located at `./proto/src/main/proto/schema.proto`

Services methods are pretty much self-explanatory.
For sensors those are

```
service SensorService{
  rpc GetSensors(SearchTagsRequest) returns (stream SensorInfoResponse) {};
  rpc GetHistoryForSensor(SensorHistoryRequest) returns (SensorHistoryResponse) {};
  rpc RegisterSensor(SensorRegistrationRequest) returns (SensorRegistrationResponse) {};
  rpc SendMeasurements(stream MeasurementRequest) returns (google.protobuf.Empty) {};
}
```
And for subscribers (*clients*) are defined in the `SensorClientService`
```
service SensorClientService{
  rpc RegisterClient(ClientRegistrationRequest) returns (TokenResponse) {};
  rpc LoginClient(ClientLoginRequest) returns (TokenResponse) {};
  rpc SubscribeOnSensor(stream SubscribeRequest) returns (stream SubscriptionResponse);
}
```


Though worth to be noticed that tokens given to **sensors** `SensorRegistrationResponse` and **clients**
`TokenResponse` messages must be used when calling  `SendMeasurements` and `SubscribeOnSensor` accordingly as values for `Authorization` field in metadata, so
that the server authenticates who calls the procedure.

When a client calls `SubscribeOnSensor` a bidirectional stream is opened and one of three 
types of messages can be received:
- `MeasurementResponse` - a measurement data taken by sensor
- `ActionResult` - a report about client's action, e.g. if subscription was successful or not
- `OnlineStatusChange` - received if the sensor that a client is subscribed on goes *online* or *offline*
This is gathered into the following structure
```
message SubscriptionResponse{
  oneof response{
    MeasurementResponse measurement = 1;
    ActionResult actionResult = 2;
    OnlineStatusChange onlineStatusChange = 3;
  }
}
```
#### Demo js client and sensor

Along with a server repository includes a 
`js-demo` folder where `demo-client.js` and `demo-sensor.js` located.

I've made them to record a `.gif`. Yet it actually a good example that
gRPC does not really care about the language services are implemented on.