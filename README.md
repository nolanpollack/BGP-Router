# BGP-Router

## Description
This simulates a BGP Router that implements the following features:
* Accepting route announcements from simulated peer routers
* Generating new route announcements for peer routers
* Managing and compressing a forwarding table
* Forwarding data packets that arrive from simulated internet users

## Usage
To run the program, run the following command:

    ./3700router <asn> <port-ip.add.re.ss-[peer,prov,cust]> [port-ip.add.re.ss-[peer,prov,cust]] ...[port-ip.add.re.ss-[peer,prov,cust]]`


## Approach
* The program begins by initializing the router, which includes creating sockets for each port, initializing the JSON 
parser, and sending a handshake message to each neighbor. 
* At this point, the router begins a steady state, wherein it uses a select() call to listen for incoming messages on 
each port. 
* When a message is received, the router parses the message and determines what to do with it.
  * If the message is a route announcement, the router updates its forwarding table and sends the announcement to its
  neighbors.
    * If the announcement could be aggregated with a route already in the routing table, the router will aggregate the
      routes
  * If the message is a withdrawal, the router will remove the route from its forwarding table and send the withdrawal
    to its neighbors.
    * If the withdrawal requires disaggregating a route, the router will disaggregate the route and add all the routes 
      previously aggregated to that route back to the forwarding table.
    * The program also checks to see if those routes need to be re-aggregated into a new route, and does so if necessary.
  * If the message is a data packet, the router will forward the packet to the next hop in the forwarding table.

## Challenges
* My first challenge was figured out why messages wouldn't send to neighboring clients. It turns out that the method
`InetAddress.getLocalHost()` returns `"127.0.1.0"`, rather than the loopback address `"127.0.0.1"`. I had to instead specify
the localhost by using `InetAddress.getByName("localhost")` to get the correct loopback address.
* My second challenge was figuring out why my messages couldn't send. I kept getting errors about non-blocking mode, but
I knew I needed the Datagram Channels in non-blocking mode to be able to use select(). The solution was sending from the 
Datagram Channel itself, rather than the Datagram Channel's socket.
* My third challenge was figuring out how to disaggregate routes correctly. Though my code was able to aggregate routes,
I struggled to disaggregate routes that had been aggregated with more than two routes. I experimented with using a binary
tree to store the routes that had been aggregated, but it got too complicated. Instead, I created a new `AggregatedRoute`
class that extends my `Route` class, and adds a list of routes that had been aggregated into that route. When disaggregating,
I simply added all the routes in the `AggregatedRoute`'s list to the forwarding table, re-aggregating those that needed it.

## Testing
To test this code, I used the test harness provided to ensure
the code passed all available test cases. In addition, I created unit tests
to test small functions that weren't working properly.