const path = require('path');
const mc = require(path.resolve(process.argv[2], 'minecraft-protocol'));
const data = require(path.resolve(process.argv[2], 'minecraft-data'))('26.1');
const ids = require('./protocol-777.json');
const configuration = data.protocol.configuration.toClient.types.packet;
const shifted = {};
for (const [key, name] of Object.entries(configuration[1][0].type[1].mappings)) {
  const id = parseInt(key, 16);
  shifted['0x' + (id >= 9 ? id + 1 : id).toString(16).padStart(2, '0')] = name;
}
configuration[1][0].type[1].mappings = shifted;
const play = data.protocol.play.toClient.types.packet;
const mappings = {};
const fields = {};
for (let id = 0; id < ids.clientCount; id++) {
  const name = Object.keys(ids.client).find(name => ids.client[name] === id) || 'ignored_' + id;
  mappings['0x' + id.toString(16).padStart(2, '0')] = name;
  fields[name] = name === 'keep_alive' ? 'packet_keep_alive' : 'restBuffer';
}
play[1][0].type[1].mappings = mappings;
play[1][1].type[1].fields = fields;
const outgoing = data.protocol.play.toServer.types.packet[1][0].type[1].mappings;
data.protocol.play.toServer.types.packet_teleport_confirm = ['container', [
  { name: 'teleportId', type: 'varint' },
  { name: 'x', type: 'f64' }, { name: 'y', type: 'f64' }, { name: 'z', type: 'f64' },
  { name: 'yaw', type: 'f32' }, { name: 'pitch', type: 'f32' }
]];
for (const [name, id] of Object.entries(ids.server)) {
  for (const key of Object.keys(outgoing)) if (outgoing[key] === name) delete outgoing[key];
  outgoing['0x' + id.toString(16).padStart(2, '0')] = name;
}
const options = { host: '127.0.0.1', port: 25686, username: 'CratesProbe', auth: 'offline', version: '26.1', hideErrors: true };
const client = mc.createClient(options);
options.protocolVersion = 777;
console.log('CLIENT START');
client.on('state', state => console.log('CLIENT STATE:', state));
const packets = {};
client.on('packet', (data, metadata) => {
  packets[metadata.name] = (packets[metadata.name] || 0) + 1;
  if (client.state !== 'play') console.log('CLIENT PACKET:', metadata.name);
  if (metadata.name === 'position') {
    let id = 0;
    let offset = 0;
    for (let index = 0; index < 5; index++) {
      id |= (data[index] & 127) << (index * 7);
      offset++;
      if ((data[index] & 128) === 0) break;
    }
    client.write('teleport_confirm', { teleportId: id,
      x: data.readDoubleBE(offset), y: data.readDoubleBE(offset + 8), z: data.readDoubleBE(offset + 16),
      yaw: data.readFloatBE(offset + 48), pitch: data.readFloatBE(offset + 52) });
  }
  if (metadata.name === 'chunk_batch_finished') client.write('chunk_batch_received', { chunksPerTick: 20 });
});
client.on('login', () => { console.log('CLIENT LOGIN'); client.write('player_loaded', {}); });
client.on('error', error => console.log('CLIENT ERROR:', error.message));
client.on('kick_disconnect', packet => console.log('CLIENT KICK:', JSON.stringify(packet)));
client.on('end', reason => { console.log(JSON.stringify({ reason, packets })); process.exit(0); });
setTimeout(() => { client.end(); }, 120000);
