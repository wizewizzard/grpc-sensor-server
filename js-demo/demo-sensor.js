const grpc = require("grpc");
const protoLoader = require('@grpc/proto-loader');
const packageDef = protoLoader.loadSync("../proto/src/main/proto/schema.proto", {});
const grpcObject = grpc.loadPackageDefinition(packageDef);
const sensorsPackage = grpcObject.sensors;

const readline = require('readline');
const { exit } = require("process");

console.log('Welcome to the  sensor\'s demo app');
console.log()
let sensorToken;
let sensorId;
let measurementsStream;
let dataSender;

const sensorsClient = new sensorsPackage.SensorService("localhost:8090", grpc.credentials.createInsecure());

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: false
  });

  rl.on('line', (line) => {
    if(line.startsWith('-register')){
        const [r, name, location, ...tags] = line.split(' ');
        sensorRegister(name, location, tags);
    }
    if(line.startsWith('-whoami')){
        console.log(`Id: ${sensorId}`);
    }
    else if(line.startsWith('-startstream')){
        const [r, period, token] = line.split(' ');
        if(measurementsStream === undefined){
            console.log('Streaming random data has started')
            measurementsStream = startMeasurementStream(token);
            dataSender = sendRandomData(period);
        }
        else{
            console.log('Already streaming');
        }
    }
    else if(line.startsWith('-endstream')){
        if(measurementsStream !== undefined){
            console.log('Ending stream')
            clearInterval(dataSender);
            measurementsStream.end();
        }
        else{
            console.log('No stream is opened');
        }
    }
  })

const sensorRegister = (name, location, tags) => {
    const sensorData = { name, location, tags };
    sensorsClient.RegisterSensor(sensorData, 
        (err, response) => {
            if(response !== undefined){
                sensorToken = response.token;
                sensorId = response.id;
                console.log(`Sensor >>> Sensor was registered. Token was saved.\n`);
            }
            else{
                console.log(err.details);
            }
        });
  };

  const startMeasurementStream = (token = undefined) => {
    const metadata = new grpc.Metadata();
    metadata.add('Authorization', token === undefined ? sensorToken : token);

    const call = sensorsClient.SendMeasurements(metadata, (err, resp) => {
        if (resp) {
            console.log('Sensor >>> Streaming is over\n');
            measurementsStream = undefined;
        }
        else{
            console.log('Sensor >>> Streaming ended with an error\n', err, '\n');
            measurementsStream = undefined;
        }
    });

    return {
        write: (request) => {
            call.write(request);
        },
        end: () => {
            call.end();
        }
    }
  };

  const sendRandomData = (period = 1000) => {
    if(measurementsStream !== undefined){
        return setInterval(() => {
            const tsmp = new Date().getTime();
            const data = {'value': Math.random(), "madeAt":{"seconds":tsmp / 1000,"nanos":tsmp % 10000}}
            measurementsStream.write(data);
            console.log('Sensor >>> Sending data: ', JSON.stringify(data));
        }, period)
    }
    else{
        console.log("Create stream first\n");
    }
  }