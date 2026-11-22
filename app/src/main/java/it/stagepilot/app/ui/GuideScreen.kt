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

@Composable fun GuideScreen(light: Boolean = false, close:()->Unit) {
    BackHandler(onBack=close)
    val bgColor = if (light) android.graphics.Color.rgb(244, 245, 248) else android.graphics.Color.rgb(16, 17, 20)
    val lightCss = if (light) {
        "<style>html,body{background:#f4f5f8!important;color:#12151c!important}" +
            "h1,h2,h3,strong,b{color:#12151c!important}" +
            "a{color:#1d5fcc!important}" +
            "code,pre,.card,blockquote,table,th,td{background:#ffffff!important;color:#12151c!important;border-color:#d5dce6!important}</style>"
    } else ""
    Column(Modifier.fillMaxSize().background(Palco.Pitch).statusBarsPadding()) {
        GhostButton("‹ StagePilot",Modifier.padding(12.dp),onClick=close)
        AndroidView(modifier=Modifier.weight(1f).fillMaxWidth(),factory={context->
            WebView(context).apply {
                settings.javaScriptEnabled=false;settings.allowFileAccess=false;settings.allowContentAccess=false
                webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                    val uri=request.url
                    if(uri.scheme=="https"&&uri.host=="stagepilot.local")return false
                    if(uri.scheme in listOf("http","https"))runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(uri.toString())))}
                    return true
                }}
                setBackgroundColor(bgColor)
                val raw = context.assets.open("guida.html").bufferedReader().use{it.readText()}
                val html = if (lightCss.isNotEmpty()) raw.replace("</head>", "$lightCss</head>") else raw
                loadDataWithBaseURL("https://stagepilot.local/",html,"text/html","UTF-8",null)
            }
        },onRelease={it.destroy()})
    }
}
