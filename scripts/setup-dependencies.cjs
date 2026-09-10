// Downloads pinned dependencies for manual installation and release validation.
const fs=require('fs'),path=require('path'),crypto=require('crypto');
(async()=>{
 const version=process.argv[2]||fs.readFileSync('gradle.properties','utf8').match(/^minecraft_version=(.+)$/m)[1].trim();
 const lock=JSON.parse(fs.readFileSync('gradle/mod-dependencies.json','utf8'))[version];
 if(!lock)throw Error('No dependency lock for '+version);
 const dest=path.resolve('validation',version,'mods');fs.mkdirSync(dest,{recursive:true});
 for(const f of Object.values(lock)){
  const file=path.join(dest,f.filename);
  if(fs.existsSync(file)&&crypto.createHash('sha512').update(fs.readFileSync(file)).digest('hex')===f.sha512)continue;
  const response=await fetch(f.url);if(!response.ok)throw Error('Download failed: '+response.status);
  const bytes=Buffer.from(await response.arrayBuffer());
  if(crypto.createHash('sha512').update(bytes).digest('hex')!==f.sha512)throw Error('Dependency checksum mismatch');
  fs.writeFileSync(file,bytes);
 }console.log('Verified dependencies: '+dest);
})().catch(error=>{console.error(error);process.exitCode=1});
