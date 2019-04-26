# stream-alerts-improved

### Problem
Set off an alert, when a word occurs for more than X number of times in a sliding window of M minutes in a stream of text (like Tweets)

### Assumptions
* Time at which the tweet is generated and the time at which the tweet is processed is the same. The difference between them is zero. 
* This solution is running on a system that has has infinite resources (Not a distributed Systems problem)
* No usage of any third party softwares whether open source or proprietary. (For example: Map-Reduce, Spark, Storm or Kafka QL.
* No usage of any third party libraries.


### Approach

![](https://github.com/abkolan/stream-alerts-improved/blob/master/media/image.png?raw=true)

#### Part 1 (of 2) - Storing the values in an array 
Let the number of seconds in the interval `M` be `n`. We will maintain an array, `A` of size `n+1` against every word in a KV map. This array would be used to store the occurrences of the word. To maintain the sliding window, we would use the array in a circular fashion. This would ensure that only values that are valid for a particular window would remain in the array. To ensure that the values of the current sliding window is maintained, we also store the lastupdated timestamp in the array. Using this we would be able to infer the current state of the values in array. Explained in detail [below](#part-1---storing-the-values-in-an-array---details). 

#### Part 2 (of 2) - Storing the array as a Segment Tree for updates in O(log n) time.

We would be doing 3 operations on the array. 

* Increment a bucket/index 
* Reset a range to zero
* Get sum of the range of values. (0 to n complete range in our case)

We use a Segment Tree for the three operations, as all the three operations above can be done in O(log n) time. Get sum in our case is in the complete range it would be in O(1) time, as we have done all the heavy lifting during the updates.

#### Simple simulation of single sliding window using both concepts
A simple simulation (single instance of a sliding window) of both the concepts can be found [here](https://gist.github.com/abkolan/d786e261752d2ae76faa11fcb1645aa4).


#### Part 1 - Storing the values in an array - Details
This is easier to explain this with an example. Consider an interval of 5 seconds, i.e `n=5`. To maintain a sliding window an array of size `n+1` is created. In our case an array of size `6`. 


**Event Id** is the is the `id` of the event, does not have any significance, only used for the ease of explaining the concept. **Timestamp** is the timestamp of the event in seconds. **Value** is the value emitted at the timestamp, word frequency in our case. 

In the simplest case, the events occur in succession. Consider 5 such events, that are seperated by 1 second each. 

|Event Id| Timestamp    | Value | 
|:------:|:------------:|:-----:| 
|E1      | 00           | 1     |    
|E2      | 01           | 1     |      
|E3      | 02           | 1     |   
|E4      | 03           | 1     |   
|E5      | 04           | 1     |   

`A` is populated by modding the **Timestamp** by the interval to determine the index or the bucket, where the value is set. (Ignore the last index, we will cover that later)  
Event E1, generated at timestamp `00` would go to the bucket `0`, Event E2 generated at the timestamp `01` would go the bucket `1` and so on. And, for this simple case the array `A` would look like this. 

Bucket|0  |1  |  2|  3|  4|  5|
------|---|---|---|---|---|---|
Value |1  |1  |1  |1  |1  |-  |
Event |E1 |E2 |E3 |E4 |E5 |-  |

The sum of all the values occurred in a window can be computed by `Sum(A[0:4])`. In the above case the sum is `5`. 

---
Consider an extension of the above case, where an Event 6 occurs at `05`.

|Event Id| Timestamp    | Value | 
|:------:|:------------:|:-----:| 
|E1      | 00           | 1     |    
|E2      | 01           | 1     |      
|E3      | 02           | 1     |   
|E4      | 03           | 1     |   
|E5      | 04           | 1     |
|E6      | 05           | 1     |    

Since we are only concerned about the events that happened in last 5 seconds, we can overwrite value at bucket `0`. And, sum would give us the value of the sum in the sliding window of 5 seconds, that is from event E2 to E6. The array `A` would like this. 

Bucket|0  |1  |  2|  3|  4|  5|
------|---|---|---|---|---|---|
Value |1  |1  |1  |1  |1  |-  |
Event |E6 |E2 |E3 |E4 |E5 |-  |

---

To accurately maintain these values in all the cases, we would also need the timestamp of last seen event. This is stored in the last index of the array. 

Consider these events.

|Event Id| Timestamp    | Value | 
|:------:|:------------:|:-----:| 
|E1      | 00           | 1     |    
|E2      | 01           | 1     |      
|E3      | 07           | 1     |   

At the end of processing of Event `E2`. The array `A` would look like. The last index `5` containing the value of the timestamp of `E2`. 

Bucket|0  |1  |  2|  3|  4|  5|
------|---|---|---|---|---|---|
Value |1  |1  |0  |0  |0  |01 |
Event |E1 |E2 |-- |-- |-- |-- |

When Event `E3` is processed, we check for the lastupdated timestamp. If difference between the current timestamp and the lastupdated timestamp is greater than the interval. We reset, all the buckets to zero and then update the current bucket. At the end of processing of `E3`. The array `A` would look like. 

Bucket|0  |1  |  2|  3|  4|  5|
------|---|---|---|---|---|---|
Value |0  |0  |0  |0  |0  |01 |
Event |-- |-- |E3 |-- |-- |-- |

---

This leaves us with one pending usecase, when the events are not sequential. 
Consider these sample events.

|Event Id| Timestamp    | Value | 
|:------:|:------------:|:-----:| 
|E1      | 00           | 1     |    
|E2      | 01           | 1     |      
|E3      | 04           | 1     |   

At the end of processig of Event `E2`. The array `A` would look like. The last index `5` containing the value of the timestamp of `E2`. 

Bucket|0  |1  |  2|  3|  4|  5|
------|---|---|---|---|---|---|
Value |1  |1  |0  |0  |0  |01 |
Event |E1 |E2 |-- |-- |-- |-- |

When the event `E3` is processed, since the last updated timestamp is 01, and the current timestamp is 04. We would have to ensure that the buckets bodering that rage i.e 3-4 are reset to indicate that there were no events generated during that time. Indicated by `R`. 

Bucket|0  |1  |  2|  3|  4|  5|
------|---|---|---|---|---|---|
Value |1  |1  |0  |0  |1  |04 |
Event |E1 |E2 |R  |R  |E4 |-- |

A simulation of this can be found in a gist [here](https://gist.github.com/abkolan/d786e261752d2ae76faa11fcb1645aa4).

### Further Improvements
* The map can be garbage collected if the last updated time is passed the interval. 
* Lazy propagation can be used for the updates in the segment tree, so the updates are done only when the sum is to be computed. 

### Building and Running
**Prerequisites**
 
* JAVA on UNIX/Linux or Mac or Linux Substem for Windows<sup>*</sup>.
* bash + GNU Core Utilities for simulation.  


<sup>*</sup>not tested on Linux Substem on Windows
#### Running the project

1. **stream-alerts** is a java maven project, the project can be built into an executable jar by using.  
`mvn package`

2. This repo comes with a sample tweets (From Kaggle, the dataset has been modified only to include tweet text as is).  
The sample tweets can be extracted by running:   
`tar -xf sample-tweets.tar.gz`

3. This repo also comes with a script `gen-tweets.sh` that would randomly generate 1000000 tweets per second from the `sample-tweets` file. Tweets per minute can be modifed in the `gen-tweets.sh`

4. Run the simulation
`./gen-tweets.sh | 
java -jar target/stream-alert-0.0.1-jar-with-dependencies.jar <NUM-OCCURRENCES> <TIME-INTERVAL-IN-MIN> <IGNORE-STOP-WORDS>`


  Example:
  `./gen-tweets.sh | java -jar target/stream-alert-0.0.1-jar-with-dependencies.jar 10000 1 true`
  
  Excerpt of the output
  
 ```
 ALERT generated for word = oh
 ALERT generated for word = nice
 ALERT generated for word = that's
 ALERT generated for word = know
 ALERT generated for word = getting
 ```

  The above example would print out alerts when a non-stopword word occurrences is equal to or greater than 10000 for a sliding period of 1 minute, from the simulated tweets The counts are accurate upto a second.

