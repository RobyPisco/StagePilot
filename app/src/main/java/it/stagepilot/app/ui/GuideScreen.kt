package it.stagepilot.app.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable fun GuideScreen(close:()->Unit) {
    BackHandler(onBack=close)
    Column(Modifier.fillMaxSize().background(Palco.Pitch).statusBarsPadding()) {
        GhostButton("‹ Torna a StagePilot",Modifier.padding(12.dp),onClick=close)
        AndroidView(modifier=Modifier.weight(1f).fillMaxWidth(),factory={context->
            WebView(context).apply {
                settings.javaScriptEnabled=false;settings.allowFileAccess=false;settings.allowContentAccess=false
                webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                    val uri=request.url
                    if(uri.scheme=="https"&&uri.host=="stagepilot.local")return false
                    if(uri.scheme in listOf("http","https"))runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(uri.toString())))}
                    return true
                }}
                setBackgroundColor(android.graphics.Color.rgb(16,17,20))
                loadDataWithBaseURL("https://stagepilot.local/",context.assets.open("guida.html").bufferedReader().use{it.readText()},"text/html","UTF-8",null)
            }
        },onRelease={it.destroy()})
    }
}
