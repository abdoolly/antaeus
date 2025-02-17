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
and then gets the next date of the scheduler to run again
then clean behind it then run itself again with the new scheduled date.

#### fun billInvoices()

This is the function which runs all the logic it charges the invoices in the following steps:

- First it fetches all invoices by status filtering only **PENDING** invoices.
- It basically forEach asynchronously on the `chargeInvoice` function to make all invoices run in parallel, also adding all those parallel jobs in one array.
- Then waiting for all jobs to finish.
- Then just make a clean up step for the retry map. (explained below)


#### suspend fun chargeInvoice(invoice: Invoice)

This is the function that takes a single invoice and charge it 

- First it sends the invoice to the `paymentProvider.charge` function which should charge the invoice.
- then takes the response if it was `True` it updates the invoice status in database.
- if it was `False` then send a notification to admin and customer with messages showing that customer does not have enough balance.

**In case of exceptions we do the following**

##### CustomerNotFoundException
Notifies the admin that this invoice customer is not found assuming an admin should fix that problem.
 
##### CurrencyMismatchException
Also notifies the admin that the invoice currency mismatches that of the customer mentioning both the invoice and customer currencies.

##### NetworkException
In case of network exception we call a function called `retryChargeInvoice` in which
we retry the charging again with delay of `30 seconds` difference between each, 
and in case all 3 retries failed it will notify the admin that there is a problem in the paymentProvider.

##### Exception
This is the last case in which we should handle inside it any unexpected exception
so, we also notify the admin for him to check the problem and take the needed procedures for a fix.

#### suspend fun retryChargeInvoice(invoice: Invoice)

This is the function mentioned above in which it takes an invoice
that had a **NetworkException** and send it again to the `chargeInvoice` function
after putting the invoice id in a map called `retryMap` to be able 
to count the number of retries for each invoice.

In case all retries consumed which is only 3 retries an admin is notified 
that this is an incident and the that paymentProvider is not working properly.


---------------------------------------------

### Further Discussion on missing things 

Here I will be explaining what can be further done to make 
this solution ready for production making it fault tolerant and more reliable.

#### Single point of failure problem
we currently have only one backend in which if anything happened to it 
at the time of the periodic cycle it will fail to charge for the month.

**solution**

To be able to solve that problem we need to have more than one backend
and put a Loadbalancer to balance requests between them.
Now if any server failed another one will do the cycle.


#### After scaling problem
Since we now have at least 2 backends running our schedulers 
we have a problem in which both may run the periodic cycle at the same time and 
that would unnecessary double charge the customer.


**solution**

To be able to solve that we need to make only one server runs the periodic 
cycle and in case of failure the other backends should know so, that they can run it instead
and at anytime only one backend should run the periodic cycle.

- first thing we should do is to make a database table that stores the next periodic cycle 
with status **PENDING** and when a backend starts it the status should be changed 
to **PROCESSING** also storing which backend working on it which forces us to make something like an ID for each of our backends.

- second thing to do is to be sure that not all backends look at that status at the same time or 
we will have the same problem. To solve that problem we will start each backend with a slightly different 
scheduled time which could be configured by an environment variable.

In which for example `(backend1-Tue Dec 01 00:00 , backend2-Tue Dec 01 01:00)`
this makes an hour difference so, in case backend-1 have not changed the status 
of the periodic cycle in the database this means that there might be something wrong 
so, backend-2 should make a health check to backend-1 and in case it died it should do
the periodic cycle itself and if it was alive then it will recheck in the next hour.

Now we have solved the problem if we have multiple backends to not run the charge cycles
at the same time by making good ordering between them.

#### In case of exceptions or failures
we have problem that we are not doing a lot except for notifying an admin in case
of exceptions.

**solution**

we should of course be monitoring our system , 
all failure cases should be reported including the charge exceptions ,
all should be reported in a single place to have a bird eye view of the system state.


#### More sophisticated handling for customers with no balance
In case the charge was `False` this means the customer did not have enough money his account
we just notify them and don't do any further thing which is a problem since we need to wait 
for next month to charge them.

**solution**

To solve this issue we can make a database table in which we save all those failed invoices of that type
and make another scheduler for them that runs one time every day or 3 days
to retry those customers that had no balance and retry their invoices again, 
and if any customer had reach 10 retries and all failed we should disable his account or send him a warning.
 

### Final words
I hope I have not missed anything and if I did please tell me what was it.

Made with :heart: to Pleo.io

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


