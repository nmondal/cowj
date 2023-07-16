// data stores
redis = _ds.get('redis')
jdbc = _ds.get('pgsql')


isRedisActive = true
try {
redis.ping()
}
catch(err) {
isRedisActive = false
}


// request body as JSON
// schema validation for GET call not working
input = JSON.parse(req.body());
person1 = input.person_id_1;
person2 = input.person_id_2;

// utilities
function getDataFromSql(person, jdbc, redis) {
findPerson = `select * from locationService.locations where person_id = "${person}";`;
con = jdbc.connection().value();
stmt = con.createStatement()
data = stmt.executeQuery(findPerson)

res = ''
while (data.next())
      {
      res = data.getString('data');
      }
      if(res != '') {
      if(isRedisActive) {
      redis.set(person, res)
      }
      res = JSON.parse(res)
      }
      return res;
}

function distance(lat1, lon1, lat2, lon2, unit) {
// This piece is from stack overflow
// calculates straight line distance between two locations
    var radlat1 = Math.PI * lat1/180
    var radlat2 = Math.PI * lat2/180
    var theta = lon1-lon2
    var radtheta = Math.PI * theta/180
    var dist = Math.sin(radlat1) * Math.sin(radlat2) + Math.cos(radlat1) * Math.cos(radlat2) * Math.cos(radtheta);
    dist = Math.acos(dist)
    dist = dist * 180/Math.PI
    dist = dist * 60 * 1.1515
    if (unit=="K") { dist = dist * 1.609344 }
    if (unit=="M") { dist = dist * 0.8684 }
    return dist
}

//business logic

dist = 0
d1 = isRedisActive ? JSON.parse(redis.get(person1)) : undefined;
d2 = isRedisActive ? JSON.parse(redis.get(person2)) : undefined;

if(!d1 || !isRedisActive) {
d1 = getDataFromSql(person1, jdbc, redis);
}
if(!d2 || !isRedisActive) {
d2 = getDataFromSql(person2, jdbc, redis);
}

if(d1 && d2) {
dist = distance(d1.latitude, d1.longitude, d2.latitude, d2.longitude, 'K' )

}

//return value
JSON.stringify({ distance : Math.abs(dist).toString(), unit : 'KM', last_seen : [{ person : person1, last_seen: d1 ? d1.last_seen : undefined }, {person : person2, last_seen: d2 ? d2.last_seen : undefined}]})