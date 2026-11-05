package com.jessy.search
import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executor
import java.util.concurrent.Executors
class MainActivity:ComponentActivity(){
 private lateinit var web:WebView
 private val executor:Executor=Executors.newSingleThreadExecutor()
 @SuppressLint("SetJavaScriptEnabled")
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);web=WebView(this);web.settings.javaScriptEnabled=true;web.settings.domStorageEnabled=true;web.addJavascriptInterface(Bridge(),"AndroidBridge");web.webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(v:WebView?,r:WebResourceRequest?):Boolean{val s=r?.url?.scheme?:return true;return s!="http"&&s!="https"}};setContentView(web);web.loadUrl(BuildConfig.API_BASE_URL)}
 inner class Bridge{
  @JavascriptInterface fun setProxy(host:String,port:Int):Boolean{if(!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)||host.isBlank()||host.contains("/")||port !in 1..65535)return false;return try{ProxyController.getInstance().setProxyOverride(ProxyConfig.Builder().addProxyRule("$host:$port").build(),executor){};true}catch(_:Exception){false}}
  @JavascriptInterface fun clearProxy():Boolean{if(!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE))return false;return try{ProxyController.getInstance().clearProxyOverride(executor){};true}catch(_:Exception){false}}
 }
 override fun onDestroy(){if(::web.isInitialized){web.removeJavascriptInterface("AndroidBridge");web.destroy()};super.onDestroy()}
}