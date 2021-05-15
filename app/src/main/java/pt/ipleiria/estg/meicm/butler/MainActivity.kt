package pt.ipleiria.estg.meicm.butler

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.wifi.WifiManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.format.Formatter
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import com.google.android.material.snackbar.Snackbar
import io.ktor.application.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import pl.droidsonroids.gif.GifDrawable
import pt.ipleiria.estg.meicm.butler.databinding.ActivityMainBinding
import java.time.LocalDateTime
import java.util.*


class MainActivity : AppCompatActivity(), RecognitionListener, TextToSpeech.OnInitListener {

    private val PERMISSIONS_REQUEST_RECORD_AUDIO = 1

    private val x = 0

    private var speech: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null

    private var detectedKeyword: Boolean = false
    private val keyword: String = "mordomo"

    private val LOG_TAG = "VoiceRecognitionActivity"

    private lateinit var binding: ActivityMainBinding

    private val serverIP = "192.168.1.78:7579"
    private val serverURI = "http://" + this.serverIP

    private lateinit var deviceIp: String

    private var receivedLocationNotification: MutableLiveData<String> = MutableLiveData<String>()
    private var receivedSentenceNotification: MutableLiveData<String> = MutableLiveData<String>()
    private val managerContainerURI = "/onem2m/butler/iproomcnt"
    private val currentRoomContainerURI = "/onem2m/location/currentroomcnt"
    private val sentencesToReadContainerURI = "/onem2m/butler/speakcnt"
    private lateinit var roomName: String
    private var active: MutableLiveData<Boolean> = MutableLiveData<Boolean>()
    private val client: OkHttpClient = OkHttpClient()

    private var tts: TextToSpeech? = null
    private var runningSpeech: MutableLiveData<Boolean> = MutableLiveData()

    private var recognitionText: MutableLiveData<String> = MutableLiveData<String>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        active.postValue(false)
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        deviceIp = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        binding.progressBar1.visibility = View.INVISIBLE

        setNotPresent()

        (binding.gif.drawable as GifDrawable).stop()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSIONS_REQUEST_RECORD_AUDIO
            )
        }

        CoroutineScope(Dispatchers.Default).launch {
            checkRoomName()
            checkIfIsActive()
        }

        embeddedServer(Jetty, 1400) {
            routing {
                post("/location") {
                    val receiveText = call.receiveText()
                    Log.d("NOTIFICATION", receiveText)
                    receivedLocationNotification.postValue(receiveText)
                }
                post("/sentences") {
                    val receiveText = call.receiveText()
                    Log.d("NOTIFICATION", receiveText)
                    receivedSentenceNotification.postValue(receiveText)
                }
            }
        }.start(wait = false)


        runningSpeech.observeForever {
            if (it == false) {
                speech!!.startListening(recognizerIntent)
            }
        }

        receivedLocationNotification.observeForever {
            if (it != null) {
                //desligar voice recognition -> como no exemplo que ja fiz "My application"
                readNotification("location", it)
            }
        }

        receivedSentenceNotification.observeForever {
            if (it != null && active.value == true) {
                readNotification("sentence", it)
            }
        }

        active.observeForever {
            if (it != null) {
                if (it) {
                    binding.progressBar1.visibility = View.VISIBLE
                    binding.textView1.visibility = View.VISIBLE
                    binding.errorView1.visibility = View.VISIBLE
                    binding.progressBar1.isIndeterminate = true

                    binding.gif.colorFilter = null
                    binding.gif.alpha = 1.0F

                    resetSpeechRecognizer()
                    setRecogniserIntent()

                    tts = TextToSpeech(this, this)
                    tts!!.setOnUtteranceProgressListener(SpeechListener(speech!!, runningSpeech))
                    speech!!.startListening(recognizerIntent)
                    //ativa escuta, fala, mostra tudo
                } else {
                    //desativa escuta, fala, esconde tudo

                    setNotPresent()

                    binding.progressBar1.visibility = View.INVISIBLE
                    binding.textView1.visibility = View.INVISIBLE
                    binding.errorView1.visibility = View.INVISIBLE
                    if (tts != null && speech != null) {
                        tts!!.stop()
                        speech!!.destroy()
                    }

                }
            }
        }

        recognitionText.observeForever {
            if (it != null) {
                sentenceToAnswer(it)
            }
        }
    }

    private fun setNotPresent() {
        val matrix = ColorMatrix()
        matrix.setSaturation(0f)
        val filter = ColorMatrixColorFilter(matrix)
        binding.gif.colorFilter = filter
        binding.gif.alpha = 0.09F
    }

    private fun checkIfIsActive() {
        var responseContainer = query("$currentRoomContainerURI?fu=1&ty=4")
        if (responseContainer != "Not found " && responseContainer.isNotEmpty()) {

            var resp = JSONObject(responseContainer)
            val respArray = resp["m2m:uril"] as JSONArray
            if (respArray.length() > 0) {
                responseContainer = query(respArray[0] as String)
                resp = JSONObject(responseContainer)
                if (resp.has("m2m:cin")) {
                    resp = resp.getJSONObject("m2m:cin")
                    if (resp.has("con")) {
                        if (resp.getString("con") == roomName) {
                            active.postValue(true)
                        }
                    }
                }
            }
        }
    }

    private fun readNotification(notfSource: String, notf: String) {

        var jsonObject = JSONObject(notf)
        var sur = ""
        if (jsonObject.has("m2m:sgn")) {
            jsonObject = jsonObject.getJSONObject("m2m:sgn")
            if (jsonObject.has("sur")) {
                sur = jsonObject.getString("sur")
                if (jsonObject.has("nev")) {
                    jsonObject = jsonObject.getJSONObject("nev")
                    if (jsonObject.has("rep")) {
                        jsonObject = jsonObject.getJSONObject("rep")
                        if (jsonObject.has("m2m:cin")) {
                            jsonObject = jsonObject.getJSONObject("m2m:cin")
                            if (notfSource == "location") {
                                if (sur == "$currentRoomContainerURI/$deviceIp" && jsonObject.getString(
                                        "ty"
                                    ).toInt() == 4
                                ) {
                                    active.postValue(
                                        jsonObject.getString("con")
                                            .equals(roomName, ignoreCase = true)
                                    )
                                }
                            }
                            if (notfSource == "sentence") {
                                if (sur == "$sentencesToReadContainerURI/$deviceIp" && jsonObject.getString(
                                        "ty"
                                    ).toInt() == 4
                                ) {
                                    speech!!.stopListening()
                                    println(speech.toString())

                                    tts!!.speak(
                                        jsonObject.getString("con"),
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        "1"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkRoomName() {

        val responseContainer = query("$managerContainerURI/$deviceIp")
        if (responseContainer != "Not found" && responseContainer.isNotEmpty()) {
            var resp = JSONObject(responseContainer)
            if (resp.has("m2m:dbg")) {
                if (resp["m2m:dbg"] == "resource does not exist") {
                    showSnack("There are no room for this device")
                }
            }

            if (resp.has("m2m:cin")) {
                resp = resp.getJSONObject("m2m:cin")
                if (resp.has("rn") && resp.has("con")) {
                    if (resp.getString("rn") == deviceIp) {
                        roomName = resp.getString("con")
                        binding.roomNameTv.text = roomName.capitalize(Locale.ROOT)
                    }
                }
            }
        }
    }

    private fun sentenceToAnswer(answer: String) {
        if (answer.toLowerCase().contains("horas são") || answer.toLowerCase()
                .contains("são que horas")
        ) {
            val current = LocalDateTime.now()
            tts!!.speak(
                "são ${current.hour} horas e ${current.minute} minutos",
                TextToSpeech.QUEUE_FLUSH,
                null,
                ""
            )

        } else
            if (answer.toLowerCase().contains("dia é hoje") || answer.toLowerCase()
                    .contains("hoje é que dia")
            ) {
                val current = LocalDateTime.now()
                tts!!.speak(
                    mappingDays(current.dayOfWeek.toString()),
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    ""
                )

            } else {
                speech!!.startListening(recognizerIntent)
            }
    }

    private fun mappingDays(day: String): String {
        when (day.toLowerCase()) {
            "monday" -> return "segunda"
            "tuesday" -> return "terça"
            "wednesday" -> return "quarta"
            "thursday" -> return "quinta"
            "friday" -> return "sexta"
            "saturday" -> return "sábado"
            "sunday" -> return "domingo"
        }
        return "Eu não sei"
    }

    private fun query(parameters: String): String {
        var responseToReturn = ""
        try {
            val request: Request = Request.Builder()
                .url(serverURI + parameters)
                .addHeader("Accept", "application/json")
                .addHeader("X-M2m-RI", "00001")
                .build()
            client.newCall(request).execute().use { response ->
                responseToReturn = if (!response.isSuccessful && response.code != 404) {
                    "Not found"
                } else {
                    response.body?.string() ?: ""
                }
            }
        } catch (e: Exception) {
            showSnack("Query Request failed")
        }
        return responseToReturn
    }

    private fun showSnack(message: String) {
        val snack =
            Snackbar.make(this.findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
        snack.setAction("Dismiss") { snack.dismiss() }
        snack.show()
    }

    private fun resetSpeechRecognizer() {
        speech?.destroy()
        speech = SpeechRecognizer.createSpeechRecognizer(this)
        Log.i(LOG_TAG, "isRecognitionAvailable: " + SpeechRecognizer.isRecognitionAvailable(this))
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speech!!.setRecognitionListener(this)
        } else finish()
    }

    private fun setRecogniserIntent() {
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recognizerIntent!!.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
            "pt-PT"
        )
        recognizerIntent!!.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        recognizerIntent!!.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }


    override fun onResume() {
        Log.i(LOG_TAG, "resume")
        super.onResume()

        //   resetSpeechRecognizer()
        //   speech!!.startListening(recognizerIntent)
    }

    override fun onPause() {
        Log.i(LOG_TAG, "pause")
        super.onPause()
        //speech!!.stopListening()
    }

    override fun onStop() {
        Log.i(LOG_TAG, "stop")
        super.onStop()
        if (speech != null) {
            speech!!.destroy()
        }
    }


    override fun onBeginningOfSpeech() {
        Log.i(LOG_TAG, "onBeginningOfSpeech")
        binding.progressBar1.isIndeterminate = false
        binding.progressBar1.max = 10
    }

    override fun onBufferReceived(buffer: ByteArray) {
        Log.i(LOG_TAG, "onBufferReceived: $buffer")
    }

    override fun onEndOfSpeech() {
        Log.i(LOG_TAG, "onEndOfSpeech")
        binding.progressBar1.isIndeterminate = true
        speech!!.stopListening()
    }

    override fun onResults(results: Bundle) {
        Log.i(LOG_TAG, "onResults")
        val matches = results
            .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = ""
        println(matches!![0])
        binding.textView1.text = text


        //se a palavra chave foi detetada anteriormente e resultados maior que zero
        if (detectedKeyword && matches.size != 0) {

            //se o resultado for outra vez a palavra chave
            if (matches[0].equals(keyword)) {
                tts!!.speak("Diga", TextToSpeech.QUEUE_FLUSH, null, "")
            } else {

                //se for um comando, tenta responder
                binding.textView1.text = matches[0]
                detectedKeyword = false
                sentenceToAnswer(matches[0])
            }

            //se a palavra chave for detetada
        } else if (matches[0].equals(keyword)) {
                (binding.gif.drawable as GifDrawable).start()
                binding.textView1.text = "detected"
                detectedKeyword = true
                tts!!.speak("Diga", TextToSpeech.QUEUE_FLUSH, null, "")
                //speech!!.startListening(recognizerIntent)
            } else {
                //se a palavra chave nao foi detetada agora nem anteriormente

                //tts!!.speak("Não percebi", TextToSpeech.QUEUE_FLUSH, null, "")
                speech!!.startListening(recognizerIntent)
            }
    }

    override fun onError(errorCode: Int) {
        val errorMessage = getErrorText(errorCode)
        Log.i(LOG_TAG, "FAILED $errorMessage")
        binding.errorView1.text = errorMessage

        if (errorCode ==  SpeechRecognizer.ERROR_NO_MATCH  ){
            speech!!.startListening(recognizerIntent)
        }

        if (errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY){
            speech!!.stopListening()
        }
    }

    override fun onEvent(arg0: Int, arg1: Bundle?) {
        Log.i(LOG_TAG, "onEvent")
    }

    override fun onPartialResults(arg0: Bundle?) {
        Log.i(LOG_TAG, "onPartialResults")
    }

    override fun onReadyForSpeech(arg0: Bundle?) {
        Log.i(LOG_TAG, "onReadyForSpeech")
    }

    override fun onRmsChanged(rmsdB: Float) {
        //Log.i(LOG_TAG, "onRmsChanged: " + rmsdB);
        binding.progressBar1.progress = rmsdB.toInt()
    }

    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
            SpeechRecognizer.ERROR_SERVER -> "error from server"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Didn't understand, please try again."
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // set US English as language for tts
            val result = tts!!.setLanguage(Locale.getDefault())

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The Language specified is not supported!")
            }

        } else {
            Log.e("TTS", "Initilization Failed!")
        }
    }

}