This project involves Distributed Averaging System based on UDP.
This project simulates master-slave architecure where multiple Slaves can send values
to a specific Master.
The Master on it`s side stores numbers which were sent by Slaves and computes the average 
value when requested.
You can run the program from cmd by following a directory where the project is and then writing:
"java DAS <port number>". If no Masters are running - the Master mode will launch. If not -
the Slave mode will be launched.

**Master Mode:**

The program listens for upcoming UDP packets from Slaves. 
When the packet is received, the following is being processed:
1. If the value is valid (e.g. 42) then it is stored in the master's intelrnal lsit;
2. If the value is 0, the master computes the average of all stored values, rounds the result
and broadcasts it to the network.
3. If the value is -1, the session is terminated and the master stops working.

   
**Slave Mode:**

The program sends a numric value to the master using the UDP protocol.
The following ruless apply:
1. The slave sends the provided number as the second parameter in the terminal command
2. If the number is 0, it instructs the master to calculate the average and broadcast it.
3. If the number is -1, it signals the master to terminate the session.
After each sent value to the Master the Slave terminates it's session.
