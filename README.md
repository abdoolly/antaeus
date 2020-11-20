## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.


## Solution explanation

As I have understood from the instructions that we need to 
implement the logic for the billingService in which it should run 
periodically every month to take invoices and charge them by using the paymentProvider
object.

### Main changes to notice

- In AntaeusApp.kt line 73 in which I have added a call for my main 
function which is runBillingScheduler (more details on what this function do below).

- And of course you should open the BillingService.kt file in which you will find all of the 
logic that applies the charging operation.

- Also you may notice a new test file added which called BillingServiceTest.kt file 
in which it has some tests for the most important functions in our BillingService class.
 
### BillingService Class

#### fun runBillingScheduler(date: Date? = null)
This is the function that starts everything in which in the 
first run it does not take any date then in subsequent runs it 
has a date parameter sent to it which is the old date to get the next time after it.

mainly this function runs a `timer.schedule` that call the main `billingInvoice` function
and then get the next date that this scheduler should run again
then clean behind it then run itself again with the new scheduled date.

#### fun billInvoices()

This is the function which runs all the logic it charges the invoices in the following steps:
- First it fetches all invoices by status filtering only PENDING invoices
- It basically forEach asynchronously on the `chargeInvoice` function to make all invoices run in parallel,also adding all those parallel jobs in one array.
- then waiting for all jobs to finish
- then just make a clean up step for the retry map


#### suspend fun chargeInvoice(invoice: Invoice)

This is the function that takes a single invoice and charge it 
- First it sends the invoice to the `paymentProvider.charge` function which should charge the invoice
- then taking the response if it was `True` it updates the invoice status in database.
- if it the charge was `False` then send a notification to admin and customer with messages showing that customer does not have enough balance

**In case of exceptions we do the following**

##### CustomerNotFoundException
notifies the admin that this invoice customer is not found assuming an admin should fix that problem
 
##### CurrencyMismatchException
Also notifies the admin that the invoice currency mismatches the customers mentioning both the invoice and customer currencies

##### NetworkException
In case of network exception we call a function called `retryChargeInvoice` in which
we retry the charging again with delay of `30 seconds` difference between each
and in case all 3 retries failed it also notifies the admin that there is a problem in the paymentProvider.

##### Exception
This is the last case in which we should handle inside it any unexpected exception
so, also we notified the admin to see what is the problem and take the needed procedures to fix the problem.

#### suspend fun retryChargeInvoice(invoice: Invoice)

This is the function mentioned above in which it takes an invoice
that had a NetworkException and send it again to the charge invoice
after putting the invoice id in a map called `retryMap` to be able 
to count the number of retries for each invoice

in case all retries consumed which is only 3 retries an admin is notified 
that this an incident and the paymentProvider is not working properly.


---------------------------------------------

### Further Discussion on missing things 

Here I will be explaining what can be further done to make 
this solution ready for production making it fault tolerant and more reliable.

#### Single point failure problem
we currently have only one backend in which if anything happened to it 
at the time of the periodic cycle running it will not run for this month

**solution**

to be able to solve that problem we will make multiple at least two backends
and put a Loadbalancer to balance requests between them.
now if one failed another one will do the cycle.


#### After scaling problem
since we now have at least 2 backends running our schedulers 
we have problem in which both may run the periodic cycle in which 
this will make each invoice double charged or more according to how many backends we have.

**solution**

To be able to solve that we need to make only one server runs the periodic 
cycle and in case of failure other backends should know so, that they can run it instead
and at anytime any one backend starts the cycle no other backend should do that.

- first thing we should do is make a database table that have the next periodic cycle 
saved in it with status PENDING and when a backend start on it the status should be changed 
to PROCESSING also storing which backend working on it which forces us to make something like name for each of our backends.

- second thing to do is to be sure that not all backend look at that status at the same time or 
will have the same problem to solve that we will start each backend with a slightly different 
periodic time could be configured by an environment variable.
in which for example the (backend1-Tue Dec 01 00:00 ,backend2-Tue Dec 01 01:00)
this makes an hour difference so, in case the backend 1 have not changed the status 
of the periodic cycle in the database this means that there might be something wrong 
so, backend 2 should make a health check to backend 1 and in case it's died it should do
the periodic cycle itself and if it was alive then it will recheck in the next hour.

now we have solved the problem if we have multiple backends to not run the charge cycles
at the same time and make some ordering to solve that.

#### In case of failures 
we have problem that we not doing a lot except for notifying an admin in case
of exceptions.

**solution**

we should of course be monitoring our system and 
all failure cases should be reported including the charge exceptions 
all should be reported in a single place to have a look at the state of our system.


#### More sophisticated handling for customers with no balance
In case the charge was `False` this means the customer did not have enough money his account
we just notify them and don't do any further thing which is a problem since we need to wait 
for next month to charge them.

**solution**

To solve this issue we can make a database table in which we save all those invoices
which failed for no balance and make another scheduler that runs every day or 3 days one time 
to retry those customers that has no balance and retry the invoice again.
and if they had like 10 retries then disabled that customer's account.  
 

### Final words
I hope I have not missed anything and if I did please tell me what was it.

---------------------------------------------

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will schedule payment of those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

## Instructions

Fork this repo with your solution. Ideally, we'd like to see your progression through commits, and don't forget to update the README.md to explain your thought process.

Please let us know how long the challenge takes you. We're not looking for how speedy or lengthy you are. It's just really to give us a clearer idea of what you've produced in the time you decided to take. Feel free to go as big or as small as you want.

## Developing

Requirements:
- \>= Java 11 environment

Open the project using your favorite text editor. If you are using IntelliJ, you can open the `build.gradle.kts` file and it is gonna setup the project in the IDE for you.

### Building

```
./gradlew build
```

### Running

There are 2 options for running Anteus. You either need libsqlite3 or docker. Docker is easier but requires some docker knowledge. We do recommend docker though.

*Running Natively*

Native java with sqlite (requires libsqlite3):

If you use homebrew on MacOS `brew install sqlite`.

```
./gradlew run
```

*Running through docker*

Install docker for your platform

```
docker build -t antaeus
docker run antaeus
```

### App Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── buildSrc
|  | gradle build scripts and project wide dependency declarations
|  └ src/main/kotlin/utils.kt 
|      Dependencies
|
├── pleo-antaeus-app
|       main() & initialization
|
├── pleo-antaeus-core
|       This is probably where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|       Module interfacing with the database. Contains the database 
|       models, mappings and access layer.
|
├── pleo-antaeus-models
|       Definition of the Internal and API models used throughout the
|       application.
|
└── pleo-antaeus-rest
        Entry point for HTTP REST API. This is where the routes are defined.
```

### Main Libraries and dependencies
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library
* [Sqlite3](https://sqlite.org/index.html) - Database storage engine

Happy hacking 😁!


