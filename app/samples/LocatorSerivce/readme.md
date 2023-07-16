Locator Service - 

This service exposes two APIs one to be used by the users to update their respective location as longitude and latitude coordinates.
The other API to be used to query between two user and get the distance between them in kilometers.

SETUP -

1) run the local redis service
2) run mysql server 

update the connection URL for both in the service.yaml file

> **_Run the below table creation command in mysql  :_**  
> 
> Create database locationService;
> 
> CREATE TABLE locations (person_id VARCHAR(255), data JSON);
> 
> alter table locations ADD CONSTRAINT UQ_person_id UNIQUE(person_id);
>

APIs -

1) POST /user/poll : users can poll this API to update their recent location coordinates.
    This API also return a UNIX timestamp stating the time at which service acknowledged there request.
2) GET /calculateDistance : Query between two users. Also returns a last seen timestamp for both the users.


> **_Samples :_**  
> **POST /user/poll**
> 
>  curl --location 'http://localhost:5006/user/poll' \
--header 'Content-Type: application/json' \
--data '
> {"latitude" : "10.01", "longitude" : "19", "personId" : "tesUser1"}'
>
> response - {"last_seen":1689487420383}
> 
> 
> **GET /calculateDistance**
> 
>curl --location --request GET 'http://localhost:5006/calculateDistance' \
      --header 'Content-Type: text/plain' \
      --data '{
"person_id_1" : "tesUser1",
    "person_id_2" : "tesUser2"
}'
> 
> response - 
> 
> {"distance":"850.7842447445607","unit":"KM","last_seen":[{"person":"tesUser1","last_seen":1689451613443},{"person":"tesUser2","last_seen":"1689451288250"}]}
> 
> gives distance as 0 if one of user is not registered with the service with no last_seen timestamp : 
> 
> {"distance":"0","unit":"KM","last_seen":[{"person":"tesUser1","last_seen":1689451613443},{"person":"tesUser3"}]} 
>


> **_Limitations :_**  
> person_id needs to unique
> 
> request schema validation is not working for GET /calculateDistance. schema validation works when API is converted to POST API.
>
> logging not working with console.log or print() for javascript

