package com.linbit.linstor.api.protobuf.internal;

import com.linbit.linstor.InternalApiConsts;
import com.linbit.linstor.api.ApiCallRc;
import com.linbit.linstor.api.ApiCallRcImpl;
import com.linbit.linstor.api.ApiCallReactive;
import com.linbit.linstor.api.protobuf.ProtoDeserializationUtils;
import com.linbit.linstor.api.protobuf.ProtobufApiCall;
import com.linbit.linstor.core.apicallhandler.controller.internal.CtrlAuthResponseApiCallHandler;
import com.linbit.linstor.netcom.Peer;
import com.linbit.linstor.proto.common.ApiCallResponseOuterClass.ApiCallResponse;
import com.linbit.linstor.proto.javainternal.s2c.MsgIntAuthResponseOuterClass.MsgIntAuthResponse;
import com.linbit.linstor.storage.kinds.ExtToolsInfo;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import reactor.core.publisher.Flux;

@ProtobufApiCall(
    name = InternalApiConsts.API_AUTH_RESPONSE,
    description = "Called by the satellite to indicate that controller authentication was handled",
    requiresAuth = false,
    transactional = true
)
@Singleton
public class IntAuthResponse implements ApiCallReactive
{
    private final CtrlAuthResponseApiCallHandler ctrlAuthResponseApiCallHandler;
    private final Provider<Peer> peerProvider;

    @Inject
    public IntAuthResponse(
        CtrlAuthResponseApiCallHandler ctrlAuthResponseApiCallHandlerRef,
        Provider<Peer> peerProviderRef
    )
    {
        ctrlAuthResponseApiCallHandler = ctrlAuthResponseApiCallHandlerRef;
        peerProvider = peerProviderRef;
    }

    @Override
    public Flux<byte[]> executeReactive(InputStream msgDataIn)
        throws IOException
    {
        return executeReactive(peerProvider.get(), msgDataIn, false)
            .thenMany(Flux.<byte[]>empty());
    }

    public Flux<ApiCallRc> executeReactive(
        Peer peer,
        InputStream msgDataIn,
        boolean waitForFullSyncAnswer
    )
        throws IOException
    {
        MsgIntAuthResponse msgAuthResponse = MsgIntAuthResponse.parseDelimitedFrom(msgDataIn);
        String nodeNameStr = peer.getNode().getName().displayValue;
        String apiCallPrefix = "(" + nodeNameStr + ") ";
        ApiCallRcImpl apiCallResponse = new ApiCallRcImpl();
        for (ApiCallResponse protoApiCallResponse : msgAuthResponse.getResponsesList())
        {
            apiCallResponse.addEntry(ProtoDeserializationUtils.parseApiCallRc(
                protoApiCallResponse, apiCallPrefix
            ));
        }

        final boolean success = msgAuthResponse.getSuccess();
        final Long expectedFullSyncId;
        final Integer linstorVersionMajor;
        final Integer linstorVersionMinor;
        final Integer linstorVersionPatch;
        final List<ExtToolsInfo> externalToolsInfoList;
        final String nodeUname;
        if (success)
        {
            expectedFullSyncId = msgAuthResponse.getExpectedFullSyncId();
            nodeUname = msgAuthResponse.getNodeUname();
            linstorVersionMajor = msgAuthResponse.getLinstorVersionMajor();
            linstorVersionMinor = msgAuthResponse.getLinstorVersionMinor();
            linstorVersionPatch = msgAuthResponse.getLinstorVersionPatch();
            externalToolsInfoList = ProtoDeserializationUtils.parseExternalToolInfoList(
                msgAuthResponse.getExtToolsInfoList(),
                false
            );
        }
        else
        {
            expectedFullSyncId = null;
            nodeUname = null;
            linstorVersionMajor = null;
            linstorVersionMinor = null;
            linstorVersionPatch = null;
            externalToolsInfoList = null;
        }
        return ctrlAuthResponseApiCallHandler.authResponse(
            peer,
            success,
            apiCallResponse,
            expectedFullSyncId,
            nodeUname,
            linstorVersionMajor,
            linstorVersionMinor,
            linstorVersionPatch,
            externalToolsInfoList,
            waitForFullSyncAnswer
        );
    }
}
