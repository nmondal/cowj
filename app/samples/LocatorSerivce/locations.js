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
data = jdbc.select(findPerson, []);
result = data.value();
res = [];
 result.forEach( m => {
           resTemp = m.get("data");
           timeStamp = m.get('created_date');
           resTemp.last_seen = timeStamp;
           res.push(resTemp)
       });
      return res;
}

function checkIfUserRegistered(person, jdbc) {
findPerson = `select * from locationService.latestLocation where person_id = "${person}";`;
data = jdbc.select(findPerson, []);
result = data.value();

res = ''
result.forEach( m => {res = m.get("data");});
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


JSON.stringify({ person_id : person, locations : data})
//{ person_id : person, locations : data}