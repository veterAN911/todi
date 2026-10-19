package com.todi.app.presentation.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.todi.app.R

private const val CODE_LENGTH = 6

@Composable
fun BackupSignInDialog(
    awaitingCodeFor: String?,
    busy: Boolean,
    onRequestCode: (String) -> Unit,
    onVerify: (String, String) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val awaiting = awaitingCodeFor != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (awaiting) R.string.backup_login_code_title else R.string.backup_login_title,
                ),
            )
        },
        text = {
            Column {
                if (awaiting) {
                    Text(stringResource(R.string.backup_login_code_sent, awaitingCodeFor))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { typed -> code = typed.filter(Char::isDigit).take(CODE_LENGTH) },
                        label = { Text(stringResource(R.string.backup_login_code)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                    )
                } else {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        label = { Text(stringResource(R.string.backup_login_email)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (awaiting) {
                        awaitingCodeFor?.let { onVerify(it, code) }
                    } else {
                        onRequestCode(email)
                    }
                },
                enabled = !busy && (if (awaiting) code.length == CODE_LENGTH else email.isNotBlank()),
            ) {
                Text(
                    stringResource(
                        if (awaiting) R.string.backup_login_confirm else R.string.backup_login_send,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = if (awaiting) onBack else onDismiss) {
                Text(
                    stringResource(
                        if (awaiting) R.string.backup_login_other_email else R.string.cancel,
                    ),
                )
            }
        },
    )
}
