package pt.ipleiria.estg.meicm.butler

import android.media.AudioManager
import android.speech.SpeechRecognizer
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.MutableLiveData

class SpeechListener(var speech: SpeechRecognizer, var b: MutableLiveData<Boolean>) :
    UtteranceProgressListener() {
    override fun onDone(utteranceId: String?) {
        AudioManager.ADJUST_MUTE
        b.postValue(false)
    }

    override fun onError(utteranceId: String?) {

    }

    override fun onStart(utteranceId: String?) {
        speech.stopListening()
        AudioManager.ADJUST_UNMUTE
        b.postValue(true)
    }
}