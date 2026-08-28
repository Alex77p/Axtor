package com.ayushdebbarma.myaiagent;

public final class VoiceServiceState {
  private static volatile boolean running=false;
  private VoiceServiceState(){}
  public static boolean isRunning(){return running;}
  public static void setRunning(boolean value){running=value;}
}
