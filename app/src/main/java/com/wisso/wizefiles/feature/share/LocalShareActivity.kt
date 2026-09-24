package com.wisso.wizefiles.feature.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.feature.transfer.formatTransferPath
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toAppPathOrNull
import com.wisso.wizefiles.storage.path.toUriString
import com.wisso.wizefiles.ui.MaterialFeatureScreen
import com.wisso.wizefiles.ui.MaterialFeatureScreen.ButtonKind
import com.wisso.wizefiles.util.AppLog

class LocalShareActivity:AppCompatActivity(){
    private lateinit var screen:MaterialFeatureScreen
    private val handler=Handler(Looper.getMainLooper())
    private var pendingPreset:AppPath?=null
    private var pendingProfileId:String?=null
    private var pendingPickedPath:AppPath?=null
    private var pendingPickedProfileId:String?=null
    private val picker=registerForActivityResult(FileListActivity.OpenDirectoryContract()){path->val profileId=pendingProfileId;pendingProfileId=null;AppLog.i(LOG_TAG,"Folder picker completed selected=${path!=null} addToExistingProfile=${profileId!=null}");if(path!=null){pendingPickedPath=path;pendingPickedProfileId=profileId;handler.post(::showPendingPickerDialogWhenReady)}}
    private val refresh=object:Runnable{override fun run(){render();handler.postDelayed(this,1_000)}}

    override fun onCreate(state:Bundle?){super.onCreate(state);screen=MaterialFeatureScreen(this,R.string.local_share_title);screen.install();pendingPreset=intent.getStringExtra(EXTRA_PATH)?.toAppPathOrNull()}
    override fun onResume(){super.onResume();handler.post(refresh)}
    override fun onPostResume(){super.onPostResume();showPendingPickerDialogWhenReady()}
    override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(hasFocus)handler.post(::showPendingPickerDialogWhenReady)}
    override fun onPause(){handler.removeCallbacks(refresh);super.onPause()}
    override fun onSupportNavigateUp():Boolean{onBackPressedDispatcher.onBackPressed();return true}

    private fun showPendingPickerDialogWhenReady(){if(!hasWindowFocus()||!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return;val path=pendingPickedPath?:return;val profileId=pendingPickedProfileId;pendingPickedPath=null;pendingPickedProfileId=null;if(profileId==null)showProfileEditor(path)else addRoot(profileId,path)}

    private fun render(){
        screen.clear();val active=LocalShareService.current
        screen.intro("Manage selected folders from a PC browser on the same trusted network. Wi-Fi or Ethernet LAN is required; mobile data is not supported. No relay server or internet account is used.",R.drawable.ic_share_control_normal_24dp)
        if(active!=null){active(active);return}
        val profiles=ShareDatabase.profiles();profiles.forEach{profile->card(profile)}
        screen.card{button("Add sharing profile"){val preset=pendingPreset;pendingPreset=null;if(preset!=null)showProfileEditor(preset)else picker.launch(null)}}
    }

    private fun active(active:ActiveLocalShare){
        heading("Sharing is active");text("Browser address");selectable("${active.scheme}://${active.address}:${active.port}");text("Pairing code");selectable(active.pairingCode)
        val qrUrl="${active.scheme}://${active.address}:${active.port}/?token=${active.qrToken}";addView(ImageView(this).apply{setImageBitmap(qr(qrUrl));adjustViewBounds=true},240)
        if(active.ftpPort>0){heading("FTP / FTPS compatibility");selectable("${active.address}:${active.ftpPort}");selectable("User: ${active.ftpUser}");selectable("Password: ${active.ftpPassword}");if(active.certificateFingerprint.isNotBlank())selectable("Certificate SHA-256: ${active.certificateFingerprint}")}
        val clients=LocalShareService.activeClients();if(clients.isNotEmpty()){heading("Connected clients");clients.forEach{client->val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};row.addView(TextView(this).apply{text=client;setPadding(0,dp(12),dp(12),dp(12))},LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));row.addView(screen.buttonView("Revoke",ButtonKind.OUTLINED){LocalShareService.revokeClient(client);render()});screen.add(row)}}
        val waiting=ShareDatabase.pendingActions(active.sessionId)
        if(waiting.isNotEmpty()){heading("Waiting for approval");waiting.forEach{action->text("${action.type.name.lowercase().replace('_',' ')} • ${action.sourceRelativePath}");val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};row.addView(screen.buttonView("Approve"){approve(action)});row.addView(screen.buttonView("Reject",ButtonKind.OUTLINED){ShareDatabase.setPendingActionState(action.id,PendingShareActionState.REJECTED);render()});screen.add(row)}}
        button("Stop sharing",ButtonKind.OUTLINED){startService(Intent(this@LocalShareActivity,LocalShareService::class.java).setAction(LocalShareService.ACTION_STOP))}
    }

    private fun approve(action:PendingShareAction){runCatching{ShareDatabase.setPendingActionState(action.id,PendingShareActionState.APPROVED);val profile=ShareDatabase.profile(LocalShareService.current?.profileId.orEmpty())?:error("Session ended");ShareMutationRunner(ShareGateway(profile,ShareDatabase.roots(profile.id)),action.sessionId).executeApproved(action)}.onFailure{Toast.makeText(this@LocalShareActivity,it.message,Toast.LENGTH_LONG).show()};render()}

    private fun card(profile:ShareProfile){screen.card{heading(profile.name);ShareDatabase.roots(profile.id).forEach{root->text("• ${root.alias}: ${formatTransferPath(this@LocalShareActivity,root.appPathUri)}")};text("${profile.permission.name.lowercase().replace('_',' ')} • Browser${if(profile.protocols.size>1)" + FTP compatibility" else ""}");button("Add selected folder",ButtonKind.OUTLINED){pendingProfileId=profile.id;picker.launch(null)};button("Start sharing"){runCatching{LocalNetworkSelector.select(this@LocalShareActivity)}.onSuccess{ContextCompat.startForegroundService(this@LocalShareActivity,Intent(this@LocalShareActivity,LocalShareService::class.java).setAction(LocalShareService.ACTION_START).putExtra(LocalShareService.EXTRA_PROFILE_ID,profile.id))}.onFailure{Toast.makeText(this@LocalShareActivity,it.message?:"Connect to a Wi-Fi or Ethernet LAN",Toast.LENGTH_LONG).show()}}}}

    private fun addRoot(profileId:String,path:AppPath){val profile=ShareDatabase.profile(profileId)?:return;val input=EditText(this).apply{hint="Shared folder name";setText(path.name.ifBlank{"Shared folder"})};val inputContainer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(24),0,dp(24),0);addView(input)};MaterialAlertDialogBuilder(this).setTitle("Add selected folder").setMessage(pathMessage(path)).setView(inputContainer).setNegativeButton(android.R.string.cancel,null).setPositiveButton("Add"){_,_->val uri=path.toUriString();SharePathSecurity.requireShareableRoot(uri);val roots=ShareDatabase.roots(profileId)+ShareRoot(profileId=profileId,alias=input.text.toString().ifBlank{path.name},appPathUri=uri,providerIdentity=providerIdentity(uri));ShareDatabase.saveProfile(profile.copy(updatedAtMillis=System.currentTimeMillis()),roots);render()}.show()}

    private fun showProfileEditor(path:AppPath){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(24),0,dp(24),0)}
        val name=EditText(this).apply{hint="Profile name";setText("PC Access")};val alias=EditText(this).apply{hint="Shared folder name";setText(path.name.ifBlank{"Shared folder"})};val full=CheckBox(this).apply{text="Full management (rename, move and delete)"};val approval=CheckBox(this).apply{text="Require phone approval for destructive browser actions";isChecked=true;isEnabled=false};val ftp=CheckBox(this).apply{text="Enable FTP compatibility (read-only by default)"};val ftps=CheckBox(this).apply{text="Enable HTTPS / FTPS device certificate"};val ftpWrite=CheckBox(this).apply{text="Allow FTP writes (requires Full management)";isEnabled=false};full.setOnCheckedChangeListener{_,checked->approval.isEnabled=checked;ftpWrite.isEnabled=checked;if(!checked){approval.isChecked=true;ftpWrite.isChecked=false}};box.addView(name);box.addView(alias);box.addView(full);box.addView(approval);box.addView(ftp);box.addView(ftps);box.addView(ftpWrite)
        MaterialAlertDialogBuilder(this).setTitle("Share selected folder").setMessage(pathMessage(path)).setView(box).setNegativeButton(android.R.string.cancel,null).setPositiveButton("Save"){_,_->
            val profile=ShareProfile(name=name.text.toString().ifBlank{"PC Access"},protocols=buildSet{add(ShareProtocol.BROWSER);if(ftp.isChecked)add(ShareProtocol.FTP);if(ftps.isChecked)add(ShareProtocol.FTPS)},permission=if(full.isChecked)SharePermission.FULL_MANAGEMENT else SharePermission.EXCHANGE_FILES,approveDestructiveInBrowser=approval.isChecked,ftpWritable=ftpWrite.isChecked,certificateAlias=if(ftps.isChecked)"wizefiles-local-sharing" else "")
            val uri=path.toUriString();SharePathSecurity.requireShareableRoot(uri);val root=ShareRoot(profileId=profile.id,alias=alias.text.toString().ifBlank{path.name},appPathUri=uri,providerIdentity=providerIdentity(uri));ShareDatabase.saveProfile(profile,listOf(root));render()
        }.show()
    }

    private fun providerIdentity(uri:String):String {val parsed=runCatching{java.net.URI(uri)}.getOrNull();return "${parsed?.scheme?:uri.substringBefore(':')}:${parsed?.host?:parsed?.authority?.substringAfter('@')?.substringBefore(':')?:"local"}"}
    private fun pathMessage(path:AppPath):String {val uri=path.toUriString();val friendly=formatTransferPath(this@LocalShareActivity,uri);return if(uri.startsWith("file:")||uri.startsWith("content:"))friendly else "$friendly\n\nThis remote folder will pass through the phone and may use internet or remote-network data."}
    private fun qr(value:String):Bitmap{val matrix=QRCodeWriter().encode(value,BarcodeFormat.QR_CODE,600,600);return Bitmap.createBitmap(600,600,Bitmap.Config.RGB_565).also{bitmap->for(y in 0 until 600)for(x in 0 until 600)bitmap.setPixel(x,y,if(matrix[x,y])0xff000000.toInt() else 0xffffffff.toInt())}}
    private fun heading(value:String)=screen.heading(value)
    private fun text(value:String)=screen.text(value)
    private fun selectable(value:String)=screen.selectable(value)
    private fun button(label:String,kind:ButtonKind=ButtonKind.PRIMARY,action:()->Unit)=screen.button(label,kind,action)
    private fun addView(view:android.view.View,size:Int)=screen.addCentered(view,dp(size))
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()

    companion object {private const val LOG_TAG="LocalShare";private const val EXTRA_PATH="share_path";fun createIntent(context:Context,path:AppPath?=null)=Intent(context,LocalShareActivity::class.java).apply{path?.let{putExtra(EXTRA_PATH,it.toUriString())}}}
}
