Locator Service - 

This service exposes two APIs one to be used by the users to update their respective location as longitude and latitude coordinates.
The other API to be used to query between two user and get the distance between them in kilometers.

SETUP -

1) run the local redis service
2) run mysql server 

update the connection URL for both in the service.yaml file

> **_Run the below table creation command in mysql  :_**  
> 
>Create database locationService; 
> 
>CREATE TABLE latestLocation (person_id VARCHAR(255), data JSON);
> 
> alter table latestLocation ADD CONSTRAINT UQ_person_id UNIQUE(person_id); 
> 
> CREATE TABLE locations (person_id VARCHAR(255), data JSON, created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP); 
> 
>alter table locations ADD CONSTRAINT UQ_location_time UNIQUE(person_id, created_date);

APIs -

1) POST /user/poll : users can poll this API to update their recent location coordinates.
    This API also return a UNIX timestamp stating the time at which service acknowledged there request.
2) POST /calculateDistance : Query between two users. Also returns a last seen timestamp for both the users.
3) POST /locations : get all the location coordinates for a user between certain time interval

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
> **POST /calculateDistance**
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
> 
>  **POST /locations**
> 
> curl --location 'http://localhost:5006/locations' \
--header 'Content-Type: text/plain' \
--data '{ "person_id" : "hardik", "start_time" : "2018-01-01 12:00:00", "end_time" : "2024-01-01 23:30:00" }'
> 
> response - {"person_id":"hardik","locations":[{"latitude":"1.001","last_seen":"2023-07-16
16:55:11","longitude":"111.36"},{"latitude":"1.001","last_seen":"2023-07-16
17:02:36","longitude":"111.36"}]}
> 

> **_Limitations :_**  
> person_id needs to unique


