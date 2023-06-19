# Scripting for COWJ

##  Why Scripts?


### Motivation 

Cowj was build to replace code with versioned configuration - to ensure that the `back end development` does not require beyond a limited no of Engineers. As would be apparent from the main doc, the motivation is very anti establishment, and as one can find, the focus here to help business out, not to promote development or increase cost in development.

### Fallacies , Economics

There are some inherent fallacies which a developer should be aware of.
An Engineer builds engine, and engines do not get changed per day, even per year, perhaps an improvement can be found in every decade or say. In software systems this timeline gets shrunk, but even then acceptable change in engines are perhaps even 2 times a decade.

It took 3 decades of effort to move out of the B-Tree and get along with LSM Trees for the masses.
Such core algorithmic changes are what we can call Engine changes, they are the engine. Naturally these algorithms are part of the storage engine.

This is however what we do not see in `business development`.  Business Development is like building a typical building. It requires masons and not Engineers. This is entirely different from developing a bridge, and clearly building Burj Khalifa is not a simple job for masons.

Building SQL Engines are engineering, of sorts, and under no circumstances writing query on top of them are not.
Business Development requires CRUD and influx of some random `business logic` into the mix, there is nothing foundational, nothing fanciful about it.

Lot of noobs talk about `scale`.  In reality one only talks about scale when there is no fundamental problem around it. Sometimes scale poses its own problem - 1 billion customer needs to be searched in less than a second. That is not really a problem of scale, it is a matter of algorithmic and Engineering ingenuity. You do not change the algorithm to do so each day. 

Compare this to  business code - which is throwaway, all the time. Business will keep on updating the code, and there is no two ways about it. Coders will not be able to cope up with it, it is not possible. 
This culminates to the fallacy of business development - it is not development - it is almost always a hack that is there for incredibly short amount of time - with a life maximally upto a year.

There is no "practice" that takes this fallacy of business development into account, because they are paid to do the quite opposite. More changes would require more people. 

The proper bane for this fallacy has a name in enterprise software - "Custom Development" or "Solution Engineering". Most of the developers are not building any product - they are doing "Custom Development".
It is always throwaway code, always.

###  Types, Domain, Existence 

So what if we want to get a "Custom Solution" built in no time, say in less than 1 day? What does a custom solution would feel like? As again - any business is nothing but CRUD, no matter how much the "Senior Engineer" groups cry about it. 

So CRUD against what? Definitely a bunch of data sources. Data in what form? This is where the jury has 100s of different ways to get data and set data. Compression? Encoding? All of them are just triviality, in the end business data is all having some schema for NOW, which would change in next 10 days even. 

JSON is for the win. Type systems got to go, with type verification of fields to be put in as configuration in case they are needed. JSON schema, RAML and OpenAPI schemas help. One can even get into compression if need be. But for a normal business it is overkill.

So if one look at the fallacy and the economy angle, and then look at the type and domain angle, one must realize this is a matter of writing random scripts and getting it away. This is precisely what mulesoft has done, and done very well. It is not random that Salesforce gobbled them up for billions.

Enterprise Software is CRUD + Reports.
Enterprise Software is matchstick engineering or rather just write scripts which runs.

But can they run fast? 
How fast is fast enough? 
Druid Engine exposes its data via a custom SQL layer - and even with that layer it can respond with less than a second for 10 million records in a 2 GB machine. These are queries an enterprise class system would take seconds.
Speed is not really the problem of enterprise.
Agility is.
It is for being Agile alone, enterprises digitised themselves, and the first computing revolution happened.
Forget AI, the enterprise must reinvent itself to move fast, because the 2nd revolution would make many of them redundant to the core.

##  Engines


##  Interfacing 


##  Debugging




