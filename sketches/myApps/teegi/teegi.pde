// PapARt library
import fr.inria.papart.procam.*;
import fr.inria.papart.multitouch.*;
import fr.inria.papart.procam.display.*;
import org.bytedeco.javacpp.*;
import org.reflections.*;
import TUIO.*;
import toxi.geom.*;

import fr.inria.guimodes.*;

Papart papart;

// Frame location. 
int framePosX = 0;
int framePosY = 200;

boolean useProjector;
boolean noCameraMode = false;

// Undecorated frame 
public void init() {
  frame.removeNotify(); 
  frame.setUndecorated(true); 
  frame.addNotify(); 
  super.init();
}

PVector boardSize = new PVector(297, 210);   //  21 * 29.7 cm
float boardResolution = 1;  // 3 pixels / mm

void setup() {

  useProjector = true;
  int frameSizeX = 1280;
  int frameSizeY = 800;

  if (!useProjector) {
    frameSizeX = 640 * 2;
    frameSizeY = 480 * 2;
  }

  if (noCameraMode) {
    frameSizeX = 1400;
    frameSizeY = 800;
  }

  size(frameSizeX, frameSizeY, OPENGL);
  papart = new Papart(this);

  if (noCameraMode) {
    papart.initNoCamera(1);
  } else {
    if (useProjector) {
      papart.initProjectorCamera("1", Camera.Type.OPENCV);
    } else {
      papart.initCamera("0", Camera.Type.OPEN_KINECT);

      BaseDisplay display = papart.getDisplay();
      display.setDrawingSize(width, height);
    }
  }

  // MarkerBoard miniTeegiVision; 
  // this.markerBoard = new MarkerBoard(configFile, width, height);

  papart.loadSketches();
  if (!noCameraMode)
    papart.startTracking();
}



void draw() {
}

boolean test = false;

void keyPressed() {


  // Placed here, bug if it is placed in setup().
  if (key == ' ')
    frame.setLocation(framePosX, framePosY);

  if (key == 't') {
    test = !test;
  }

  if (key == '1') {
    Mode.set("raw");
  }
  if (key == '2') {
    Mode.set("relax");
  }
  if (key == '3') {
    Mode.set("vision");
  }

  if (key == '4') {
    SecondMode.set("waves");
  }
  if (key == '5') {
    SecondMode.set("pixelate");
  }
  if (key == '6') {
    SecondMode.set("noise");
  }
  if (key == '7') {
    SecondMode.set("clear");
  }

  //     SecondMode.add("clear");
  //   SecondMode.add("waves");
  //   SecondMode.add("pixelate");
  //   SecondMode.add("noise");
  //   SecondMode.set("waves");
}