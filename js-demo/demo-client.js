const grpc = require("grpc");
const protoLoader = require('@grpc/proto-loader');
const packageDef = protoLoader.loadSync("../proto/src/main/proto/schema.proto", {});
const grpcObject = grpc.loadPackageDefinition(packageDef);
const schemaPackage = grpcObject.sensors;

const readline = require('readline');
const { exit } = require("process");

console.log('Welcome to the subscriber\'s demo app');
console.log('-register <login> <password> <email>\n' +
'-login <login> <password>\n-list - lists sensors\n' +
'-subscribeon <sensorid> - subscribe on the sensor\n' +
'-unsubscribefrom <sensorid> - unsubscribe from the sensor\n' +
'-disconnect - close a bidirectional stream\n-quit - quit app\n');
let clientToken;
let subscriptionStream;

const clientSubscriber = new schemaPackage.SensorClientService("localhost:8090", grpc.credentials.createInsecure());
const clientSensors = new schemaPackage.SensorService("localhost:8090", grpc.credentials.createInsecure());

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: false
  });
  
  rl.on('line', function(line){
    if(line.startsWith('-register')){
        const [r, login, password, email] = line.split(' ');
        clientRegister(login, password, email);
    }
    else if(line.startsWith('-login')){
        const [r, login, password] = line.split(' ');
        clientLogin(login, password);
    }
    else if(line.startsWith('-list')){
        getSensors();
    }
    else if(line.startsWith('-subscribeon')){
        const [r, sensorId] = line.split(' ');
        if(subscriptionStream === undefined)
            subscriptionStream = clientSubscribeOpenStream(clientToken);
            subscriptionStream.write({"sensorId": sensorId, "disconnect": false});
    }
    else if(line.startsWith('-unsubscribefrom')){
        const [r, sensorId] = line.split(' ');
        if(subscriptionStream === undefined)
            subscriptionStream = clientSubscribeOpenStream(clientToken);
        subscriptionStream.write({"sensorId": sensorId, "disconnect": true});
    }
    else if(line.startsWith('-disconnect')){
        if(subscriptionStream !== undefined)
            subscriptionStream.end();
    }
    else if(line.startsWith('-quit')){
        if(subscriptionStream !== undefined)
            subscriptionStream.end();
        exit();
    }
  })

  const getSensors = () => {
    const call = clientSensors.getSensors({});
    console.log('Client >>> Sensors list:')
    call.on('data', (msg) => {
        console.log(JSON.stringify(msg))
    });

    call.on('end', () => {
        console.log(' ')
    })
  }

  const clientRegister = (login, password, email) => {
    const clientData = {'login': login, 'password': password, "email": email};
    clientSubscriber.RegisterClient(clientData, 
        (err, response) => {
            if(response !== undefined){
                clientToken = response.token;
                console.log(`Client >>> You are registered. Token was saved.`, '\n');
            }
            else{
                console.log(err.details);
            }
        });
  }

  const clientLogin = (login, password) => {
    const clientData = {'login': login, 'password': password};
    clientSubscriber.LoginClient(clientData, 
        (err, response) => {
            if(response !== undefined){
                clientToken = response.token;
                console.log(`Client >>> You are logged in. Token was saved.`, '\n');
            }
            else{
                console.log('Client >>> Login failed: ', err.details, '\n');
            }
        });
  }

  const clientSubscribeOpenStream = (clientToken) => {
    const metadata = new grpc.Metadata();
    metadata.add('Authorization', clientToken);
    const call = clientSubscriber.SubscribeOnSensor(metadata);

    call.on('data', (msg) => {
        if(msg.actionResult){
            console.log('Client >>> ', msg.actionResult.message, '\n');
        }
        else if(msg.measurement){
            const date = new Date(msg.measurement.madeAt.seconds.low * 1000);
            console.log(`Client >>> Measurement received. \n    From: ${msg.measurement.sensorId}\n    Value: ${msg.measurement.value}\n    Made at: ${date}`, '\n');
        }
        else if(msg.onlineStatusChange){
            console.log('Client >>> Sensor\'s online status changed. Id: ', msg.onlineStatusChange.sensorId, ' - ', 
            getEnumNameByValue(schemaPackage.SensorOnlineStatus, msg.onlineStatusChange.onlineStatus).name, '\n');
        }
        else{
            console.log('Unknown format of message\n');
        }
    });

    call.on('end', () => {
        console.log('Client >>> Subscription stream is over\n');
        subscriptionStream = undefined;
    });

    call.on('error', (err) => {
        console.log('Client >>> Subscription ended with an error\n', err);
        subscriptionStream = undefined;
    });

    return {
        write: (request) => {
            call.write(request);
        },
        end: () => {
            call.end();
        }
    };
  };

  const getEnumNameByValue = (protoEnum, value) => {
    return protoEnum.type.value.find(item => {
        return item.number == value;
    });
  }