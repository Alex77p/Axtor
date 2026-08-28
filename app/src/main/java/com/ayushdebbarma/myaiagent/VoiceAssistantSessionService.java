package com.ayushdebbarma.myaiagent;
import android.service.voice.*;
public class VoiceAssistantSessionService extends VoiceInteractionSessionService {
 @Override public VoiceInteractionSession onNewSession(android.os.Bundle args){return new VoiceAssistantSession(this);}
}
