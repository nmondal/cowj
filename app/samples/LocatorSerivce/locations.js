// data stores
jdbc = _ds.get('pgsql')

// request body as JSON
// schema validation for GET call not working
input = req.attribute("_body");
person = input.get('person_id')
startTime = input.get('start_time')
endTime = input.get('end_time')

// utilities
function getDataFromSql(person, startTime, endTime, jdbc) {
findPerson = `select * from locationService.locations where person_id = "${person}" AND created_date BETWEEN '${startTime}' AND '${endTime}';`;
con = jdbc.connection().value();
data = jdbc.select(findPerson);
//data = stmt.executeQuery()

res = [];
while (data.next())
      {
      resTemp = JSON.parse(data.getString('data'));
      timeStamp = data.getString('created_date');
      resTemp.last_seen = timeStamp;
      res.push(resTemp)
      }
      return res;
}

function checkIfUserRegistered(person, jdbc) {
findPerson = `select * from locationService.latestLocation where person_id = "${person}";`;
con = jdbc.connection().value();
stmt = con.createStatement()
data = stmt.executeQuery(findPerson)

res = ''
while (data.next())
      {
      res = data.getString('data');
      }
      if(res != '') {
      res = JSON.parse(res)
      }
      return res;
}

//business logic

data = [];
try {

Test.panic(checkIfUserRegistered(person, jdbc) == '', 'User not found', 404)
data = getDataFromSql(person, startTime, endTime, jdbc);

} catch (err) {
Test.expect(false, err, 400 )
}

//return value

//getting [object Object] in the postman when trying to pass JSON as an object at the last
//JSON needs to stringified at the last
// uncomment the last line to reproduce
JSON.stringify({ person_id : person, locations : data})
//{ person_id : person, locations : data}