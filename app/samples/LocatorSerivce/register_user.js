// data stores
redis = _ds.get('redis')
jdbc = _ds.get('pgsql')

// request body as JSON
input = JSON.parse(req.body())

//business logic
invalidLocation = parseInt(input.latitude) > 180 || parseInt(input.latitude) < -180
 || parseInt(input.longitude) > 180 || parseInt(input.longitude) < -180

Test.expect(!invalidLocation, "invalid location coordinates", 422 )

lastSeen = Date.now()
redis.set(input.personId, JSON.stringify( { latitude : input.latitude, longitude : input.longitude, last_seen : lastSeen}))

// insert or update the data to mysql table. This can be moved to a async processor to further speed up the API
findPerson = `select * from locationService.locations where person_id = "${input.personId}";`;
con = jdbc.connection().value();
stmt = con.createStatement()
data = stmt.executeQuery(findPerson)

res = ''
while (data.next())
      {
      res+=data.getString('person_id')
      }

if(res == '') {
// insert a new entry
insertSql = `insert into locations VALUES( "${input.personId}" ,'{"latitude": "${input.latitude}", "longitude":  "${input.longitude}", "last_seen":  "${lastSeen}"}');`
stmt = con.createStatement()
data = stmt.execute(insertSql)
} else {
// update existing entry
updateSql = `UPDATE locations
 set data = '{"latitude": "${input.latitude}", "longitude":  "${input.longitude}", "last_seen":  "${lastSeen}"}'
 where person_id = "${input.personId}" ;`
 stmt = con.createStatement()
 data = stmt.execute(updateSql)
}

JSON.stringify( { last_seen : lastSeen})
