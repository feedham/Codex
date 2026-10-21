package pk.bar.membermanager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
private fun PasswordField(value:String,onChange:(String)->Unit,label:String,busy:Boolean){
    var visible by remember{mutableStateOf(false)}
    OutlinedTextField(value,onChange,label={Text(label)},singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),
        visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon={IconButton(onClick={visible=!visible},enabled=!busy){Icon(if(visible)Icons.Default.VisibilityOff else Icons.Default.Visibility,if(visible)"Hide password" else "Show password")}},
        modifier=Modifier.fillMaxWidth(),enabled=!busy)
}

@Composable
internal fun AuthScreen(signUp:Boolean,width:WindowWidthSizeClass,vm:AppViewModel,onSwitch:()->Unit){
    var first by rememberSaveable{mutableStateOf("")};var last by rememberSaveable{mutableStateOf("")}
    var email by rememberSaveable{mutableStateOf("")}
    var password by remember{mutableStateOf("")};var confirmation by remember{mutableStateOf("")}
    var reset by rememberSaveable{mutableStateOf(false)};var code by remember{mutableStateOf("")}
    var refreshRecovery by rememberSaveable{mutableStateOf(false)}
    val busy by vm.authBusy.collectAsState();val recovery by vm.recoveryCode.collectAsState()
    Box(Modifier.fillMaxSize().background(Color(0xFF080D16)).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),contentAlignment=Alignment.Center){
        Column(Modifier.widthIn(max=440.dp).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Bar Members List",style=MaterialTheme.typography.titleLarge)
            if(recovery!=null){
                Text("Save your recovery code",style=MaterialTheme.typography.headlineSmall)
                Text("Store this code somewhere private. It lets you reset your password on this app/device without email or phone verification. A new code replaces the previous one.")
                SelectionContainer{Text(recovery.orEmpty(),style=MaterialTheme.typography.titleMedium,modifier=Modifier.fillMaxWidth().padding(12.dp))}
                Button(onClick={password="";confirmation="";code="";reset=false;vm.finishRecovery()},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("I saved my code - continue")}
            }else{
                Text(if(reset)"Reset password" else if(signUp)"Create your account" else "Welcome back",style=MaterialTheme.typography.headlineSmall)
                if(signUp&&!reset){
                    OutlinedTextField(first,{first=it},label={Text("First name")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy)
                    OutlinedTextField(last,{last=it},label={Text("Last name")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy)
                }
                OutlinedTextField(email,{email=it},label={Text("Email")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth(),enabled=!busy)
                if(reset){
                    Text("Enter the recovery code you saved from this app. If you never saved a code, sign in with your existing password first to set one up.",style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(code,{code=it},label={Text("Recovery code")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy)
                }
                PasswordField(password,{password=it},if(reset)"New password" else "Password",busy)
                if(reset)PasswordField(confirmation,{confirmation=it},"Confirm new password",busy)
                if(!signUp&&!reset)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Checkbox(refreshRecovery,{refreshRecovery=it},enabled=!busy);Text("Create a new recovery code",style=MaterialTheme.typography.bodySmall)}
                Button(onClick={if(reset)vm.resetPassword(email,code,password,confirmation)else vm.authenticate(signUp,first,last,email,password,refreshRecovery)},enabled=!busy&&email.isNotBlank()&&password.length>=6&&(!reset||(code.isNotBlank()&&confirmation.isNotBlank())),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(if(busy)"Please wait..." else if(reset)"Reset password" else if(signUp)"Create account" else "Sign in")}
                if(!signUp&&!reset)TextButton(onClick={reset=true;password=""},enabled=!busy){Text("Forgot password?")}
                TextButton(onClick={password="";confirmation="";code="";if(reset)reset=false else onSwitch()},enabled=!busy){Text(if(reset)"Back to sign in" else if(signUp)"Already have an account? Sign in" else "New here? Create account")}
            }
        }
    }
}
