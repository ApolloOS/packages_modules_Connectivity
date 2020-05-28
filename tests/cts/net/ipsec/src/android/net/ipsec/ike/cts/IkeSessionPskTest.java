/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.ipsec.ike.cts;

import static android.app.AppOpsManager.OP_MANAGE_IPSEC_TUNNELS;
import static android.net.ipsec.ike.exceptions.IkeProtocolException.ERROR_TYPE_AUTHENTICATION_FAILED;
import static android.net.ipsec.ike.exceptions.IkeProtocolException.ERROR_TYPE_INTERNAL_ADDRESS_FAILURE;
import static android.net.ipsec.ike.exceptions.IkeProtocolException.ERROR_TYPE_NO_PROPOSAL_CHOSEN;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.net.LinkAddress;
import android.net.ipsec.ike.IkeFqdnIdentification;
import android.net.ipsec.ike.IkeSession;
import android.net.ipsec.ike.IkeSessionParams;
import android.net.ipsec.ike.exceptions.IkeProtocolException;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "MANAGE_IPSEC_TUNNELS permission can't be granted to instant apps")
public class IkeSessionPskTest extends IkeSessionTestBase {
    // Test vectors for success workflow
    private static final String SUCCESS_IKE_INIT_RESP =
            "46B8ECA1E0D72A18B45427679F9245D421202220000000000000015022000030"
                    + "0000002C010100040300000C0100000C800E0080030000080300000203000008"
                    + "0200000200000008040000022800008800020000A7AA3435D088EC1A2B7C2A47"
                    + "1FA1B85F1066C9B2006E7C353FB5B5FDBC2A88347ED2C6F5B7A265D03AE34039"
                    + "6AAC0145CFCC93F8BDB219DDFF22A603B8856A5DC59B6FAB7F17C5660CF38670"
                    + "8794FC72F273ADEB7A4F316519794AED6F8AB61F95DFB360FAF18C6C8CABE471"
                    + "6E18FE215348C2E582171A57FC41146B16C4AFE429000024A634B61C0E5C90C6"
                    + "8D8818B0955B125A9B1DF47BBD18775710792E651083105C2900001C00004004"
                    + "406FA3C5685A16B9B72C7F2EEE9993462C619ABE2900001C00004005AF905A87"
                    + "0A32222AA284A7070585601208A282F0290000080000402E290000100000402F"
                    + "00020003000400050000000800004014";
    private static final String SUCCESS_IKE_AUTH_RESP =
            "46B8ECA1E0D72A18B45427679F9245D42E20232000000001000000EC240000D0"
                    + "0D06D37198F3F0962DE8170D66F1A9008267F98CDD956D984BDCED2FC7FAF84A"
                    + "A6664EF25049B46B93C9ED420488E0C172AA6635BF4011C49792EF2B88FE7190"
                    + "E8859FEEF51724FD20C46E7B9A9C3DC4708EF7005707A18AB747C903ABCEAC5C"
                    + "6ECF5A5FC13633DCE3844A920ED10EF202F115DBFBB5D6D2D7AB1F34EB08DE7C"
                    + "A54DCE0A3A582753345CA2D05A0EFDB9DC61E81B2483B7D13EEE0A815D37252C"
                    + "23D2F29E9C30658227D2BB0C9E1A481EAA80BC6BE9006BEDC13E925A755A0290"
                    + "AEC4164D29997F52ED7DCC2E";
    private static final String SUCCESS_CREATE_CHILD_RESP =
            "46B8ECA1E0D72A18B45427679F9245D42E20242000000002000000CC210000B0"
                    + "484565D4AF6546274674A8DE339E9C9584EE2326AB9260F41C4D0B6C5B02D1D"
                    + "2E8394E3CDE3094895F2ACCABCDCA8E82960E5196E9622BD13745FC8D6A2BED"
                    + "E561FF5D9975421BC463C959A3CBA3478256B6D278159D99B512DDF56AC1658"
                    + "63C65A986F395FE8B1476124B91F83FD7865304EB95B22CA4DD9601DA7A2533"
                    + "ABF4B36EB1B8CD09522F6A600032316C74E562E6756D9D49D945854E2ABDC4C"
                    + "3AF36305353D60D40B58BE44ABF82";
    private static final String SUCCESS_DELETE_CHILD_RESP =
            "46B8ECA1E0D72A18B45427679F9245D42E202520000000030000004C2A000030"
                    + "0C5CEB882DBCA65CE32F4C53909335F1365C91C555316C5E9D9FB553F7AA916"
                    + "EF3A1D93460B7FABAF0B4B854";
    private static final String SUCCESS_DELETE_IKE_RESP =
            "46B8ECA1E0D72A18B45427679F9245D42E202520000000040000004C00000030"
                    + "9352D71100777B00ABCC6BD7DBEA697827FFAAA48DF9A54D1D68161939F5DC8"
                    + "6743A7CEB2BE34AC00095A5B8";

    private IkeSession openIkeSessionWithRemoteAddress(InetAddress remoteAddress) {
        IkeSessionParams ikeParams =
                new IkeSessionParams.Builder(sContext)
                        .setNetwork(mTunNetwork)
                        .setServerHostname(remoteAddress.getHostAddress())
                        .addSaProposal(SaProposalTest.buildIkeSaProposalWithNormalModeCipher())
                        .addSaProposal(SaProposalTest.buildIkeSaProposalWithCombinedModeCipher())
                        .setLocalIdentification(new IkeFqdnIdentification(LOCAL_HOSTNAME))
                        .setRemoteIdentification(new IkeFqdnIdentification(REMOTE_HOSTNAME))
                        .setAuthPsk(IKE_PSK)
                        .build();
        return new IkeSession(
                sContext,
                ikeParams,
                buildTunnelModeChildSessionParams(),
                mUserCbExecutor,
                mIkeSessionCallback,
                mFirstChildSessionCallback);
    }

    @BeforeClass
    public static void setUpTunnelPermissionBeforeClass() throws Exception {
        // Under normal circumstances, the MANAGE_IPSEC_TUNNELS appop would be auto-granted, and
        // a standard permission is insufficient. So we shell out the appop, to give us the
        // right appop permissions.
        setAppOp(OP_MANAGE_IPSEC_TUNNELS, true);
    }

    // This method is guaranteed to run in subclasses and will run after subclasses' @AfterClass
    // methods.
    @AfterClass
    public static void tearDownTunnelPermissionAfterClass() throws Exception {
        setAppOp(OP_MANAGE_IPSEC_TUNNELS, false);
    }

    @Test
    public void testIkeSessionSetupAndChildSessionSetupWithTunnelMode() throws Exception {
        if (!hasTunnelsFeature()) return;

        // Open IKE Session
        IkeSession ikeSession = openIkeSessionWithRemoteAddress(mRemoteAddress);
        performSetupIkeAndFirstChildBlocking(SUCCESS_IKE_INIT_RESP, SUCCESS_IKE_AUTH_RESP);

        // IKE INIT and IKE AUTH takes two exchanges. Message ID starts from 2
        int expectedMsgId = 2;

        verifyIkeSessionSetupBlocking();
        verifyChildSessionSetupBlocking(
                mFirstChildSessionCallback,
                Arrays.asList(TUNNEL_MODE_INBOUND_TS),
                Arrays.asList(TUNNEL_MODE_OUTBOUND_TS),
                Arrays.asList(EXPECTED_INTERNAL_LINK_ADDR));

        IpSecTransformCallRecord firstTransformRecordA =
                mFirstChildSessionCallback.awaitNextCreatedIpSecTransform();
        IpSecTransformCallRecord firstTransformRecordB =
                mFirstChildSessionCallback.awaitNextCreatedIpSecTransform();
        verifyCreateIpSecTransformPair(firstTransformRecordA, firstTransformRecordB);

        // Open additional Child Session
        TestChildSessionCallback additionalChildCb = new TestChildSessionCallback();
        ikeSession.openChildSession(buildTunnelModeChildSessionParams(), additionalChildCb);
        mTunUtils.awaitReqAndInjectResp(
                IKE_DETERMINISTIC_INITIATOR_SPI,
                expectedMsgId++,
                true /* expectedUseEncap */,
                SUCCESS_CREATE_CHILD_RESP);

        // Verify opening additional Child Session
        verifyChildSessionSetupBlocking(
                additionalChildCb,
                Arrays.asList(TUNNEL_MODE_INBOUND_TS),
                Arrays.asList(TUNNEL_MODE_OUTBOUND_TS),
                new ArrayList<LinkAddress>());
        IpSecTransformCallRecord additionalTransformRecordA =
                additionalChildCb.awaitNextCreatedIpSecTransform();
        IpSecTransformCallRecord additionalTransformRecordB =
                additionalChildCb.awaitNextCreatedIpSecTransform();
        verifyCreateIpSecTransformPair(additionalTransformRecordA, additionalTransformRecordB);

        // Close additional Child Session
        ikeSession.closeChildSession(additionalChildCb);
        mTunUtils.awaitReqAndInjectResp(
                IKE_DETERMINISTIC_INITIATOR_SPI,
                expectedMsgId++,
                true /* expectedUseEncap */,
                SUCCESS_DELETE_CHILD_RESP);

        verifyDeleteIpSecTransformPair(
                additionalChildCb, additionalTransformRecordA, additionalTransformRecordB);
        additionalChildCb.awaitOnClosed();

        // Close IKE Session
        ikeSession.close();
        performCloseIkeBlocking(expectedMsgId++, SUCCESS_DELETE_IKE_RESP);
        verifyCloseIkeAndChildBlocking(firstTransformRecordA, firstTransformRecordB);
    }

    @Test
    public void testIkeSessionKillWithTunnelMode() throws Exception {
        if (!hasTunnelsFeature()) return;

        // Open IKE Session
        IkeSession ikeSession = openIkeSessionWithRemoteAddress(mRemoteAddress);
        performSetupIkeAndFirstChildBlocking(SUCCESS_IKE_INIT_RESP, SUCCESS_IKE_AUTH_RESP);

        ikeSession.kill();
        mFirstChildSessionCallback.awaitOnClosed();
        mIkeSessionCallback.awaitOnClosed();
    }

    @Test
    public void testIkeInitFail() throws Exception {
        final String ikeInitFailRespHex =
                "46B8ECA1E0D72A180000000000000000292022200000000000000024000000080000000E";

        // Open IKE Session
        IkeSession ikeSession = openIkeSessionWithRemoteAddress(mRemoteAddress);
        int expectedMsgId = 0;
        mTunUtils.awaitReqAndInjectResp(
                IKE_DETERMINISTIC_INITIATOR_SPI,
                expectedMsgId++,
                false /* expectedUseEncap */,
                ikeInitFailRespHex);

        mFirstChildSessionCallback.awaitOnClosed();

        IkeProtocolException protocolException =
                (IkeProtocolException) mIkeSessionCallback.awaitOnClosedException();
        assertEquals(ERROR_TYPE_NO_PROPOSAL_CHOSEN, protocolException.getErrorType());
        assertArrayEquals(EXPECTED_PROTOCOL_ERROR_DATA_NONE, protocolException.getErrorData());
    }

    @Test
    public void testIkeAuthHandlesAuthFailNotification() throws Exception {
        final String ikeInitRespHex =
                "46B8ECA1E0D72A18CF94CE3159486F002120222000000000000001502200"
                        + "00300000002C010100040300000C0100000C800E01000300000803000005"
                        + "0300000802000004000000080400000228000088000200001821AA854691"
                        + "FA3292DF710F0AC149ACBD0CB421608B8796C1912AF04C5B4B23936FDEC4"
                        + "7CB640E3EAFB56BBB562825E87AF68B40E4BAB80A49BAD44407450A4195A"
                        + "1DD54BD99F48D28C9F0FBA315A3401C1C3C4AD55911F514A8DF2D2467C46"
                        + "A73DDC1452AE81336E0F0D5EC896D2E7A77628AF2F9089F48943399DF216"
                        + "EFCD2900002418D2B7E4E6AF0FEFF5962CF8D68F7793B1293FEDE13331D4"
                        + "AB0CE9436C2EE1EC2900001C0000400457BD9AEF5B362A83DD7F3DDAA4A9"
                        + "9B6B4041DAF32900001C000040055A81893582701E44D4B6729A22FE06DE"
                        + "82A03A36290000080000402E290000100000402F00020003000400050000"
                        + "000800004014";
        final String ikeAuthFailRespHex =
                "46B8ECA1E0D72A18CF94CE3159486F002E202320000000010000004C2900"
                        + "00301B9E4C8242D3BE62E7F0A537FE8B92C6EAB7153105DA421DCE43A06D"
                        + "AB6E4808BAC0CA1DAD6ADD0A126A41BD";

        // Open IKE Session
        IkeSession ikeSession = openIkeSessionWithRemoteAddress(mRemoteAddress);
        performSetupIkeAndFirstChildBlocking(ikeInitRespHex, ikeAuthFailRespHex);

        mFirstChildSessionCallback.awaitOnClosed();
        IkeProtocolException protocolException =
                (IkeProtocolException) mIkeSessionCallback.awaitOnClosedException();
        assertEquals(ERROR_TYPE_AUTHENTICATION_FAILED, protocolException.getErrorType());
        assertArrayEquals(EXPECTED_PROTOCOL_ERROR_DATA_NONE, protocolException.getErrorData());
    }

    @Test
    public void testIkeAuthHandlesFirstChildCreationFail() throws Exception {
        final String ikeInitRespHex =
                "46B8ECA1E0D72A182B300285DA19E6452120222000000000000001502200"
                        + "00300000002C010100040300000C0100000C800E01000300000803000005"
                        + "0300000802000004000000080400000228000088000200005C9DE629981F"
                        + "DB1FC45DB6CCF15D076C1F51BD9F63C771DC089F05CCDE6247965D15C616"
                        + "C7B5A62342491715E4D1FEA19326477D24143E8E56AB6AD93F54B19BC32A"
                        + "44BC0A5B5632E57D0A3C43E466E1547D8E4EF65EA4B864A348161666E229"
                        + "84975A486251A17C4F096A6D5CF3DB83874B70324A31AA7ADDE2D73BADD8"
                        + "238029000024CF06260F7C4923295E7C91F2B8479212892DA7A519A0322F"
                        + "F5B2BF570B92972B2900001C00004004C7ACC2C7D58CF8C9F5E953993AF4"
                        + "6CAC976635B42900001C00004005B64B190DFE7BDE8B9B1475EDE67B63D6"
                        + "F1DBBF44290000080000402E290000100000402F00020003000400050000"
                        + "000800004014";
        final String ikeAuthCreateChildFailHex =
                "46B8ECA1E0D72A182B300285DA19E6452E202320000000010000008C2400"
                        + "0070386FC9CCC67495A17915D0544390A2963A769F4A42C6FA668CEEC07F"
                        + "EC0C87D681DE34267023DD394F1401B5A563E71002C0CE0928D0ABC0C4570"
                        + "E39C2EDEF820F870AB71BD70A3F3EB5C96CA294B6D3F01677690DCF9F8CFC"
                        + "9584650957573502BA83E32F18207A9ADEB1FA";

        // Open IKE Session
        IkeSession ikeSession = openIkeSessionWithRemoteAddress(mRemoteAddress);
        performSetupIkeAndFirstChildBlocking(ikeInitRespHex, ikeAuthCreateChildFailHex);

        // Even though the child creation failed, the authentication succeeded, so the IKE Session's
        // onOpened() callback is still expected
        verifyIkeSessionSetupBlocking();

        // Verify Child Creation failed
        IkeProtocolException protocolException =
                (IkeProtocolException) mFirstChildSessionCallback.awaitOnClosedException();
        assertEquals(ERROR_TYPE_INTERNAL_ADDRESS_FAILURE, protocolException.getErrorType());
        assertArrayEquals(EXPECTED_PROTOCOL_ERROR_DATA_NONE, protocolException.getErrorData());

        ikeSession.kill();
        mIkeSessionCallback.awaitOnClosed();
    }
}
