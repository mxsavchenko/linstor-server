package com.linbit.linstor.core.apicallhandler.controller;


import com.linbit.ImplementationError;
import com.linbit.ValueOutOfRangeException;
import com.linbit.linstor.LinStorDataAlreadyExistsException;
import com.linbit.linstor.LinStorException;
import com.linbit.linstor.annotation.ApiContext;
import com.linbit.linstor.annotation.PeerContext;
import com.linbit.linstor.api.ApiCallRc;
import com.linbit.linstor.api.ApiCallRc.RcEntry;
import com.linbit.linstor.api.ApiCallRcImpl.ApiCallRcEntry;
import com.linbit.linstor.api.ApiCallRcImpl;
import com.linbit.linstor.api.ApiConsts;
import com.linbit.linstor.api.prop.LinStorObject;
import com.linbit.linstor.core.apicallhandler.response.ApiAccessDeniedException;
import com.linbit.linstor.core.apicallhandler.response.ApiDatabaseException;
import com.linbit.linstor.core.apicallhandler.response.ApiOperation;
import com.linbit.linstor.core.apicallhandler.response.ApiRcException;
import com.linbit.linstor.core.apicallhandler.response.ResponseContext;
import com.linbit.linstor.core.apicallhandler.response.ResponseConverter;
import com.linbit.linstor.core.identifier.VolumeNumber;
import com.linbit.linstor.core.objects.ResourceGroup;
import com.linbit.linstor.core.objects.ResourceGroupData;
import com.linbit.linstor.core.objects.VolumeGroup;
import com.linbit.linstor.core.objects.VolumeGroupData;
import com.linbit.linstor.core.objects.VolumeGroupDataControllerFactory;
import com.linbit.linstor.dbdrivers.DatabaseException;
import com.linbit.linstor.core.objects.VolumeGroup.VlmGrpApi;
import com.linbit.linstor.logging.ErrorReporter;
import com.linbit.linstor.netcom.Peer;
import com.linbit.linstor.propscon.Props;
import com.linbit.linstor.security.AccessContext;
import com.linbit.linstor.security.AccessDeniedException;

import static com.linbit.linstor.core.apicallhandler.controller.CtrlRscGrpApiCallHandler.getRscGrpDescriptionInline;

import javax.inject.Provider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CtrlVlmGrpApiCallHandler
{
    private final AccessContext apiCtx;
    private final ErrorReporter errorReporter;
    private final CtrlTransactionHelper ctrlTransactionHelper;
    private final Provider<AccessContext> peerAccCtx;
    private final Provider<Peer> peer;
    private final VolumeGroupDataControllerFactory volumeGroupDataFactory;
    private final CtrlPropsHelper ctrlPropsHelper;
    private final CtrlApiDataLoader ctrlApiDataLoader;
    private final ResponseConverter responseConverter;

    public CtrlVlmGrpApiCallHandler(
        @ApiContext AccessContext apiCtxRef,
        ErrorReporter errorReporterRef,
        CtrlTransactionHelper ctrlTransactionHelperRef,
        @PeerContext Provider<AccessContext> peerAccCtxRef,
        Provider<Peer> peerRef,
        VolumeGroupDataControllerFactory volumeGroupDataControllerFactoryRef,
        CtrlPropsHelper ctrlPropsHelperRef,
        CtrlApiDataLoader ctrlApiDataLoaderRef,
        ResponseConverter responseConverterRef
    )
    {
        apiCtx = apiCtxRef;
        errorReporter = errorReporterRef;
        ctrlTransactionHelper = ctrlTransactionHelperRef;
        peerAccCtx = peerAccCtxRef;
        peer = peerRef;
        volumeGroupDataFactory = volumeGroupDataControllerFactoryRef;
        ctrlPropsHelper = ctrlPropsHelperRef;
        ctrlApiDataLoader = ctrlApiDataLoaderRef;
        responseConverter = responseConverterRef;
    }

    public <T extends VlmGrpApi> List<VolumeGroupData> createVlmGrps(
        ResourceGroupData rscGrpRef,
        List<T> vlmGrpPojoListRef
    )
        throws AccessDeniedException
    {
        if (!rscGrpRef.getRscDfns(peerAccCtx.get()).isEmpty())
        {
            throw new ApiRcException(
                ApiCallRcImpl.entryBuilder(
                    ApiConsts.FAIL_EXISTS_RSC_DFN,
                    "Volume group cannot be created while the resource group has already resource definitions."
                )
                .build()
            );
        }

        List<VolumeGroupData> vlmGrps = new ArrayList<>();
        for (VlmGrpApi vlmGrpPojo : vlmGrpPojoListRef)
        {
            vlmGrps.add(createVolumeGroup(rscGrpRef, vlmGrpPojo));
        }
        return vlmGrps;
    }

    public <T extends VlmGrpApi> ApiCallRc createVlmGrps(
        String rscGrpNameRef,
        List<T> vlmGrpApiListRef
    )
    {
        ApiCallRcImpl responses = new ApiCallRcImpl();

        Map<String, String> objRefs = new TreeMap<>();
        objRefs.put(ApiConsts.KEY_VLM_GRP, rscGrpNameRef);

        ResponseContext context = new ResponseContext(
            ApiOperation.makeCreateOperation(),
            "Volume groups for " + getRscGrpDescriptionInline(rscGrpNameRef),
            "volume groups for " + getRscGrpDescriptionInline(rscGrpNameRef),
            ApiConsts.MASK_VLM_GRP,
            objRefs
        );

        try
        {
            if (vlmGrpApiListRef.isEmpty())
            {
                throw new ApiRcException(
                    ApiCallRcImpl.entryBuilder(
                        ApiConsts.MASK_WARN,
                        "Volume group list to create is empty."
                    )
                    .setDetails("Volume group list that should be added to the resource group is empty.")
                    .build()
                );
            }

            ResourceGroupData rscGrp = ctrlApiDataLoader.loadResourceGroup(rscGrpNameRef, true);

            List<VolumeGroupData> vlmGrpsCreated = createVlmGrps(rscGrp, vlmGrpApiListRef);

            ctrlTransactionHelper.commit();

            for (VolumeGroupData vlmGrp : vlmGrpsCreated)
            {
                responseConverter.addWithOp(responses, context, createVlmGrpCrtSuccessEntry(vlmGrp, rscGrpNameRef));
            }
        }
        catch (Exception | ImplementationError exc)
        {
            responses = responseConverter.reportException(peer.get(), context, exc);
        }

        return responses;
    }

    public ApiCallRc modify(
        String rscGrpNameRef,
        int vlmNrRef,
        Map<String, String> overridePropsRef,
        HashSet<String> deletePropKeysRef,
        HashSet<String> deleteNamespacesRef
    )
    {
        ApiCallRcImpl responses = new ApiCallRcImpl();

        Map<String, String> objRefs = new TreeMap<>();
        objRefs.put(ApiConsts.KEY_VLM_GRP, rscGrpNameRef);

        ResponseContext context = new ResponseContext(
            ApiOperation.makeCreateOperation(),
            "Volume groups for " + getRscGrpDescriptionInline(rscGrpNameRef),
            "volume groups for " + getRscGrpDescriptionInline(rscGrpNameRef),
            ApiConsts.MASK_VLM_GRP,
            objRefs
        );
        try
        {
            VolumeGroup vlmGrp = ctrlApiDataLoader.loadVlmGrp(rscGrpNameRef, vlmNrRef, true);
            Props props = vlmGrp.getProps(peerAccCtx.get());
            ctrlPropsHelper.fillProperties(
                LinStorObject.VOLUME_DEFINITION,
                overridePropsRef,
                props,
                ApiConsts.FAIL_ACC_DENIED_RSC
            );
            ctrlPropsHelper.remove(props, deletePropKeysRef, deleteNamespacesRef);
        }
        catch (Exception | ImplementationError exc)
        {
            responses = responseConverter.reportException(peer.get(), context, exc);
        }
        return responses;
    }

    public ApiCallRc delete(String rscGrpNameRef, int vlmNrRef)
    {
        ApiCallRcImpl responses = new ApiCallRcImpl();

        Map<String, String> objRefs = new TreeMap<>();
        objRefs.put(ApiConsts.KEY_VLM_GRP, rscGrpNameRef);

        ResponseContext context = new ResponseContext(
            ApiOperation.makeCreateOperation(),
            "Volume groups for " + getRscGrpDescriptionInline(rscGrpNameRef),
            "volume groups for " + getRscGrpDescriptionInline(rscGrpNameRef),
            ApiConsts.MASK_VLM_GRP,
            objRefs
        );
        try
        {
            VolumeGroup vlmGrp = ctrlApiDataLoader.loadVlmGrp(rscGrpNameRef, vlmNrRef, true);
            vlmGrp.delete(peerAccCtx.get());
        }
        catch (Exception | ImplementationError exc)
        {
            responses = responseConverter.reportException(peer.get(), context, exc);
        }
        return responses;

    }

    private VolumeGroupData createVolumeGroup(
        ResourceGroupData rscGrpRef,
        VlmGrpApi vlmGrpApiRef
    )
    {
        VolumeNumber vlmNr = getOrGenerateVlmNr(vlmGrpApiRef, rscGrpRef);

        ResponseContext context = makeVolumeGroupContext(
            ApiOperation.makeCreateOperation(),
            rscGrpRef.getName().displayValue,
            vlmNr.value
        );

        VolumeGroupData vlmGrp;

        try
        {
            vlmGrp = createVolumeGroupData(
                peerAccCtx.get(),
                rscGrpRef,
                vlmNr
            );

            ctrlPropsHelper.fillProperties(
                LinStorObject.VOLUME_DEFINITION,
                vlmGrpApiRef.getProps(),
                getVlmGrpProps(vlmGrp),
                ApiConsts.FAIL_ACC_DENIED_VLM_GRP
            );
        }
        catch (Exception | ImplementationError exc)
        {
            throw new ApiRcException(
                responseConverter.exceptionToResponse(
                    peer.get(),
                    context,
                    exc
                ),
                exc,
                true
            );
        }
        return vlmGrp;
    }

    private VolumeGroupData createVolumeGroupData(
        AccessContext accCtx,
        ResourceGroup rscGrp,
        VolumeNumber vlmNr
    )
    {
        VolumeGroupData vlmGrp;
        try
        {
            vlmGrp = volumeGroupDataFactory.create(
                accCtx,
                rscGrp,
                vlmNr
            );
        }
        catch (AccessDeniedException accDeniedExc)
        {
            throw new ApiAccessDeniedException(
                accDeniedExc,
                "create " + getVlmGrpDescriptionInline(rscGrp, vlmNr),
                ApiConsts.FAIL_ACC_DENIED_VLM_GRP
            );
        }
        catch (LinStorDataAlreadyExistsException dataAlreadyExistsExc)
        {
            throw new ApiRcException(
                ApiCallRcImpl.simpleEntry(
                    ApiConsts.FAIL_EXISTS_VLM_GRP,
                    String.format(
                        "A volume group with the number %d already exists in resource group '%s'.",
                        vlmNr.value,
                        rscGrp.getName().getDisplayName()
                    )
                ),
                dataAlreadyExistsExc
            );
        }
        catch (DatabaseException dbExc)
        {
            throw new ApiDatabaseException(dbExc);
        }
        return vlmGrp;
    }

    private VolumeNumber getOrGenerateVlmNr(VlmGrpApi vlmGrpApi, ResourceGroup rscGrp)
    {
        VolumeNumber vlmNr;
        try
        {
            vlmNr = CtrlRscGrpApiCallHandler.getVlmNr(vlmGrpApi, rscGrp, apiCtx);
        }
        catch (ValueOutOfRangeException valOORangeExc)
        {
            throw new ApiRcException(
                ApiCallRcImpl.simpleEntry(
                    ApiConsts.FAIL_INVLD_VLM_NR,
                    String.format(
                        "The specified volume number '%d' is invalid. Volume numbers have to be in range of %d - %d.",
                        vlmGrpApi.getVolumeNr(),
                        VolumeNumber.VOLUME_NR_MIN,
                        VolumeNumber.VOLUME_NR_MAX
                    )
                ),
                valOORangeExc
            );
        }
        catch (LinStorException linStorExc)
        {
            throw new ApiRcException(
                ApiCallRcImpl.simpleEntry(
                    ApiConsts.FAIL_POOL_EXHAUSTED_VLM_NR,
                    "An exception occured during generation of a volume number."
                ),
                linStorExc
            );
        }
        return vlmNr;
    }

    private Props getVlmGrpProps(VolumeGroupData vlmGrpRef)
    {
        Props props;
        try
        {
            props = vlmGrpRef.getProps(peerAccCtx.get());
        }
        catch (AccessDeniedException accDeniedExc)
        {
            throw new ApiAccessDeniedException(
                accDeniedExc,
                "access the properties of " + getVlmGrpDescriptionInline(vlmGrpRef),
                ApiConsts.FAIL_ACC_DENIED_VLM_GRP
            );
        }
        return props;
    }

    private RcEntry createVlmGrpCrtSuccessEntry(VolumeGroupData vlmGrp, String rscGrpNameRef)
    {
        ApiCallRcEntry vlmGrpCrtSuccessEntry = new ApiCallRcEntry();
        vlmGrpCrtSuccessEntry.setReturnCode(ApiConsts.CREATED);
        String successMessage = String.format(
            "New volume group with number '%d' of resource group '%s' created.",
            vlmGrp.getVolumeNumber().value,
            rscGrpNameRef
        );
        vlmGrpCrtSuccessEntry.setMessage(successMessage);
        vlmGrpCrtSuccessEntry.putObjRef(ApiConsts.KEY_RSC_GRP, rscGrpNameRef);
        vlmGrpCrtSuccessEntry.putObjRef(ApiConsts.KEY_VLM_NR, Integer.toString(vlmGrp.getVolumeNumber().value));
        errorReporter.logInfo(successMessage);
        return vlmGrpCrtSuccessEntry;
    }

    public static String getVlmGrpDescription(String rscGrpName, Integer vlmNr)
    {
        return "Resource group: " + rscGrpName + ", Volume number: " + vlmNr;
    }

    public static String getVlmGrpDescriptionInline(VolumeGroupData vlmGrpData)
    {
        return getVlmGrpDescriptionInline(vlmGrpData.getResourceGroup(), vlmGrpData.getVolumeNumber());
    }

    public static String getVlmGrpDescriptionInline(ResourceGroup rscGrp, VolumeNumber volNr)
    {
        return getVlmGrpDescriptionInline(rscGrp.getName().displayValue, volNr.value);
    }

    public static String getVlmGrpDescriptionInline(String rscName, Integer vlmNr)
    {
        return "volume group with number '" + vlmNr + "' of resource group '" + rscName + "'";
    }

    static ResponseContext makeVolumeGroupContext(
        ApiOperation operation,
        String  rscGrpNameStr,
        int volumeNr
    )
    {
        Map<String, String> objRefs = new TreeMap<>();
        objRefs.put(ApiConsts.KEY_RSC_GRP, rscGrpNameStr);
        objRefs.put(ApiConsts.KEY_VLM_NR, Integer.toString(volumeNr));

        return new ResponseContext(
            operation,
            getVlmGrpDescription(rscGrpNameStr, volumeNr),
            getVlmGrpDescriptionInline(rscGrpNameStr, volumeNr),
            ApiConsts.MASK_VLM_DFN,
            objRefs
        );
    }
}