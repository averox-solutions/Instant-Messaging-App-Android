/*
 * Copyright (c) 2022 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.features.crypto.verification.user

import androidx.core.text.toSpannable
import com.airbnb.epoxy.EpoxyController
import com.airbnb.mvrx.Fail
import com.airbnb.mvrx.Loading
import com.airbnb.mvrx.Success
import com.airbnb.mvrx.Uninitialized
import im.vector.app.R
import im.vector.app.core.epoxy.bottomSheetDividerItem
import im.vector.app.core.resources.ColorProvider
import im.vector.app.core.resources.StringProvider
import im.vector.app.core.utils.colorizeMatchingText
import im.vector.app.features.crypto.verification.epoxy.bottomSheetVerificationActionItem
import im.vector.app.features.crypto.verification.epoxy.bottomSheetVerificationBigImageItem
import im.vector.app.features.crypto.verification.epoxy.bottomSheetVerificationNoticeItem
import im.vector.app.features.crypto.verification.epoxy.bottomSheetVerificationWaitingItem
import im.vector.app.features.displayname.getBestName
import im.vector.app.features.html.EventHtmlRenderer
import im.vector.lib.core.utils.epoxy.charsequence.toEpoxyCharSequence
import org.matrix.android.sdk.api.session.crypto.model.RoomEncryptionTrustLevel
import org.matrix.android.sdk.api.session.crypto.verification.CancelCode
import org.matrix.android.sdk.api.session.crypto.verification.EVerificationState
import org.matrix.android.sdk.api.session.crypto.verification.QRCodeVerificationState
import org.matrix.android.sdk.api.util.MatrixItem
import javax.inject.Inject

abstract class BaseEpoxyVerificationController(
        val stringProvider: StringProvider,
        val colorProvider: ColorProvider,
        val eventHtmlRenderer: EventHtmlRenderer,
) : EpoxyController() {

    interface InteractionListener {
        fun onClickOnVerificationStart()
        fun onDone(b: Boolean)
        fun onDoNotMatchButtonTapped()
        fun onMatchButtonTapped()
        fun openCamera()
        fun doVerifyBySas()
        fun onUserDeniesQrCodeScanned()
        fun onUserConfirmsQrCodeScanned()
        fun acceptRequest()
        fun declineRequest()
    }

    var listener: InteractionListener? = null
}

class UserVerificationController @Inject constructor(
        stringProvider: StringProvider,
        colorProvider: ColorProvider,
        eventHtmlRenderer: EventHtmlRenderer,
) : BaseEpoxyVerificationController(stringProvider, colorProvider, eventHtmlRenderer) {

//    interface InteractionListener: BaseEpoxyVerificationController.InteractionListener {
//    }

//    var listener: InteractionListener? = null

    var state: UserVerificationViewState? = null

    fun update(state: UserVerificationViewState) {
//        Timber.w("VALR controller updated $state")
        this.state = state
        requestModelBuild()
    }

    override fun buildModels() {
        val state = this.state ?: return
        renderRequest(state)
    }

    private fun renderRequest(state: UserVerificationViewState) {
        val host = this
        when (state.pendingRequest) {
            Uninitialized -> {
                // let's add option to start one
                val styledText = stringProvider.getString(R.string.verification_request_notice, state.otherUserId)
                        .toSpannable()
                        .colorizeMatchingText(state.otherUserId, colorProvider.getColorFromAttribute(R.attr.vctr_notice_text_color))

                bottomSheetVerificationNoticeItem {
                    id("notice")
                    notice(styledText.toEpoxyCharSequence())
                }

                bottomSheetDividerItem {
                    id("sep")
                }
                bottomSheetVerificationActionItem {
                    id("start")
                    title(host.stringProvider.getString(R.string.start_verification))
                    titleColor(host.colorProvider.getColorFromAttribute(R.attr.colorPrimary))
                    subTitle(host.stringProvider.getString(R.string.verification_request_start_notice))
                    iconRes(R.drawable.ic_arrow_right)
                    iconColor(host.colorProvider.getColorFromAttribute(R.attr.vctr_content_primary))
                    listener { host.listener?.onClickOnVerificationStart() }
                }
            }
            is Loading -> {
                bottomSheetVerificationWaitingItem {
                    id("waiting")
                    title(host.stringProvider.getString(R.string.verification_request_waiting_for, state.otherUserMxItem.getBestName()))
                }
            }
            is Success -> {
                val pendingRequest = state.pendingRequest.invoke()
                when (pendingRequest.state) {
                    EVerificationState.WaitingForReady -> {
                        bottomSheetVerificationWaitingItem {
                            id("waiting")
                            title(host.stringProvider.getString(R.string.verification_request_waiting_for, state.otherUserMxItem.getBestName()))
                        }
                    }
                    EVerificationState.Requested -> {
                        // add accept buttons?
                        renderAcceptDeclineRequest()
                    }
                    EVerificationState.Ready -> {
                        // add start options
                        renderStartTransactionOptions(pendingRequest, false)
                    }
                    EVerificationState.Started,
                    EVerificationState.WeStarted -> {
                        // nothing to do, in this case the active transaction is shown
                        renderActiveTransaction(state)
                    }
                    EVerificationState.WaitingForDone,
                    EVerificationState.Done -> {
                        verifiedSuccessTile()
                        bottomDone()
                    }
                    EVerificationState.Cancelled -> {
                        renderCancel(pendingRequest.cancelConclusion ?: CancelCode.User)
                    }
                    EVerificationState.HandledByOtherSession -> {
                        // we should dismiss
                        bottomDone { listener?.onDone(false) }
                    }
                }
            }
            is Fail -> {
                // TODO
            }
        }
    }

//    private fun renderStartTransactionOptions(request: PendingVerificationRequest) {
//        val scanCodeInstructions = stringProvider.getString(R.string.verification_scan_notice)
//        val host = this
//        val scanOtherCodeTitle = stringProvider.getString(R.string.verification_scan_their_code)
//        val compareEmojiSubtitle = stringProvider.getString(R.string.verification_scan_emoji_subtitle)
//
//        bottomSheetVerificationNoticeItem {
//            id("notice")
//            notice(scanCodeInstructions.toEpoxyCharSequence())
//        }
//
//        if (request.weShouldDisplayQRCode && !request.qrCodeText.isNullOrEmpty()) {
//            bottomSheetVerificationQrCodeItem {
//                id("qr")
//                data(request.qrCodeText!!)
//            }
//
//            bottomSheetDividerItem {
//                id("sep0")
//            }
//        }
//
//        if (request.weShouldShowScanOption) {
//            bottomSheetVerificationActionItem {
//                id("openCamera")
//                title(scanOtherCodeTitle)
//                titleColor(host.colorProvider.getColorFromAttribute(R.attr.colorPrimary))
//                iconRes(R.drawable.ic_camera)
//                iconColor(host.colorProvider.getColorFromAttribute(R.attr.colorPrimary))
//                listener { host.listener?.openCamera() }
//            }
//
//            bottomSheetDividerItem {
//                id("sep1")
//            }
//
//            bottomSheetVerificationActionItem {
//                id("openEmoji")
//                title(host.stringProvider.getString(R.string.verification_scan_emoji_title))
//                titleColor(host.colorProvider.getColorFromAttribute(R.attr.vctr_content_primary))
//                subTitle(compareEmojiSubtitle)
//                iconRes(R.drawable.ic_arrow_right)
//                iconColor(host.colorProvider.getColorFromAttribute(R.attr.vctr_content_primary))
//                listener { host.listener?.doVerifyBySas() }
//            }
//        } else if (request.isSasSupported) {
//            bottomSheetVerificationActionItem {
//                id("openEmoji")
//                title(host.stringProvider.getString(R.string.verification_no_scan_emoji_title))
//                titleColor(host.colorProvider.getColorFromAttribute(R.attr.colorPrimary))
//                iconRes(R.drawable.ic_arrow_right)
//                iconColor(host.colorProvider.getColorFromAttribute(R.attr.vctr_content_primary))
//                listener { host.listener?.doVerifyBySas() }
//            }
//        } else {
//            // ??? can this happen
//        }
//    }

    private fun renderActiveTransaction(state: UserVerificationViewState) {
        val transaction = state.startedTransaction
        val host = this
        when (transaction) {
            is Loading -> {
                // Loading => We are starting a transaction
                bottomSheetVerificationWaitingItem {
                    id("waiting")
                    title(host.stringProvider.getString(R.string.please_wait))
                }
            }
            is Success -> {
                // Success => There is an active transaction
                renderTransaction(state, transaction = transaction.invoke())
            }
            is Fail -> {
                // todo
            }
            is Uninitialized -> {
            }
        }
    }

    private fun renderTransaction(state: UserVerificationViewState, transaction: VerificationTransactionData) {
        when (transaction) {
            is VerificationTransactionData.QrTransactionData -> {
                renderQrTransaction(transaction, state.otherUserMxItem)
            }
            is VerificationTransactionData.SasTransactionData -> {
                renderSasTransaction(transaction)
            }
        }
    }

    private fun renderQrTransaction(transaction: VerificationTransactionData.QrTransactionData, otherUserItem: MatrixItem) {
        val host = this
        when (transaction.state) {
            QRCodeVerificationState.Reciprocated -> {
                // we are waiting for confirmation from the other side
                bottomSheetVerificationNoticeItem {
                    id("notice")
                    apply {
                        notice(host.stringProvider.getString(R.string.qr_code_scanned_verif_waiting_notice).toEpoxyCharSequence())
                    }
                }

                bottomSheetVerificationBigImageItem {
                    id("image")
                    roomEncryptionTrustLevel(RoomEncryptionTrustLevel.Trusted)
                }

                bottomSheetVerificationWaitingItem {
                    id("waiting")
                    title(host.stringProvider.getString(R.string.qr_code_scanned_verif_waiting, otherUserItem.getBestName()))
                }
            }
            QRCodeVerificationState.WaitingForScanConfirmation -> {
                // we need to confirm that the other party actual scanned us
                bottomSheetVerificationNoticeItem {
                    id("notice")
                    apply {
                        val name = otherUserItem.getBestName()
                        notice(host.stringProvider.getString(R.string.qr_code_scanned_by_other_notice, name).toEpoxyCharSequence())
                    }
                }

                bottomSheetVerificationBigImageItem {
                    id("image")
                    roomEncryptionTrustLevel(RoomEncryptionTrustLevel.Trusted)
                }

                bottomSheetDividerItem {
                    id("sep0")
                }

                bottomSheetVerificationActionItem {
                    id("deny")
                    title(host.stringProvider.getString(R.string.qr_code_scanned_by_other_no))
                    titleColor(host.colorProvider.getColorFromAttribute(R.attr.colorError))
                    iconRes(R.drawable.ic_check_off)
                    iconColor(host.colorProvider.getColorFromAttribute(R.attr.colorError))
                    listener { host.listener?.onUserDeniesQrCodeScanned() }
                }

                bottomSheetDividerItem {
                    id("sep1")
                }

                bottomSheetVerificationActionItem {
                    id("confirm")
                    title(host.stringProvider.getString(R.string.qr_code_scanned_by_other_yes))
                    titleColor(host.colorProvider.getColorFromAttribute(R.attr.colorPrimary))
                    iconRes(R.drawable.ic_check_on)
                    iconColor(host.colorProvider.getColorFromAttribute(R.attr.colorPrimary))
                    listener { host.listener?.onUserConfirmsQrCodeScanned() }
                }
            }
            QRCodeVerificationState.WaitingForOtherDone,
            QRCodeVerificationState.Done -> {
                // Done
            }
            QRCodeVerificationState.Cancelled -> {
                // Done
//                renderCancel(transaction.)
            }
        }
    }

    private fun bottomDone() {
        val host = this
        bottomSheetDividerItem {
            id("sep_done")
        }

        bottomSheetVerificationActionItem {
            id("done")
            title(host.stringProvider.getString(R.string.done))
            titleColor(host.colorProvider.getColorFromAttribute(R.attr.vctr_content_primary))
            iconRes(R.drawable.ic_arrow_right)
            iconColor(host.colorProvider.getColorFromAttribute(R.attr.vctr_content_primary))
            listener { host.listener?.onDone(true) }
        }
    }
}
