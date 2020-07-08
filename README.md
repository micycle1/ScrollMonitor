# ScrollMonitor
ScrollMonitor is a horizontally-scrolling line graph GUI element for [Processing](https://processing.org/). It provides an easy way to plot real-time data on a Processing sketch.

A single ScrollMonitor is capable of plotting multiple datastreams at once.

Define a data stream by giving it a string identifier and then push data to it using that identifier.

## Aims
* Aesthetic
* Performant (as possible, without sacrificing aesthetics)
* Easy & intuitive use: user-friendly GUI interaction & a programmer-friendly API
* Well documented

## Features
* User resizable & movable
* Fully colour-customisable
* Displays data at mouse cursor on mouse-over
* Graph smoothing (using a rolling average window)
* Customisable number of horizontal and vertical divisions (gridlines)

## TODO
* Decouple datastream history size with the length of the view (so that the x-axis can be zoomed in and out to dynamically see a different range of data)

## Examples
### A ScrollMonitor with one datastream:
```
import micycle.scrollMonitor.ScrollMonitor;

ScrollMonitor FPSGraph; // declare ScrollMonitor

void setup() {

  PVector position = new PVector(100, 100); // position (of the upper-left corner)
  PVector dimensions = new PVector(700, 400); // width and height of ScrollMonitor
  int history = 300; // number of x-axis datapoints visible
  int yAxisMax = 80; // y-axis maximum/ceiling value
  
  FPSGraph = new ScrollMonitor(this, position, dimensions, history, yAxisMax);

  FPSGraph.addDataStream("fps"); // create a new datastream named "fps"
}

void draw() {

  background(255);
  
  FPSGraph.push("fps", frameRate); // push the current frameRate to the fps DataStream
  FPSGraph.run(); // update and draw the ScrollMonitor
}
```

* Multiple datastreams
* Multiple monitors
* Ordering
* Y-axis max
* Resizing and moving
* MouseOver
* Smoothing (rolling average)
* Pausing
* Time step (manual step and higher values)


A ScrollMonitor with multiple datastreams (y-axis max is defined per monitor):

Pushing multiple datapoints within a frame is permitted, but will introduce decoupling between the background and the affected graph.

