package org.traccar.protocol;

import org.junit.jupiter.api.Test;
import org.traccar.ProtocolTest;
import org.traccar.model.Position;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class TaipProtocolDecoderTest extends ProtocolTest {

    @Test
    public void testDecodeKeepAlive() throws Exception {

        var decoder = spy(inject(new TaipProtocolDecoder(null)));

        assertNull(decoder.decode(null, null, ">KA;#1F1D;ID=860693088279170;*0C<"));
        verify(decoder).getDeviceSession(isNull(), isNull(), eq("860693088279170"));

    }

    @Test
    public void testDecodeResponse() throws Exception {

        var decoder = inject(new TaipProtocolDecoder(null));

        verifyDecode(decoder, text(
                ">RTD01E0060000000;ID=860693088278438;*14<"),
                position(Checks.ATTRIBUTES).attribute(Position.KEY_RESULT, "RTD01E0060000000"));

        verifyDecode(decoder, text(
                ">RRL12E;TRG=JMD+;ACC={GER29H};ID=860693088278438;*63<"),
                position(Checks.ATTRIBUTES).attribute(Position.KEY_RESULT, "RRL12E;TRG=JMD+;ACC={GER29H}"));

        verifyDecode(decoder, text(
                ">GOK;ID=860693088278438;*57<"),
                position(Checks.ATTRIBUTES).attribute(Position.KEY_RESULT, "GOK"));

        assertNull(decoder.decode(null, null, ">GOK<"));

    }

    @Test
    public void testDecode() throws Exception {

        var decoder = inject(new TaipProtocolDecoder(null));

        verifyDecode(decoder, text(
                ">RER00251026194446-2764323-06241282000288FF001290000006B13000900001523;2010=0.00,5000=31.00,1030!3,4201!3,1020=111457.00,1010=0.00,2012=72.00,2013!3,2020!3,3010!3;#00C1;ID=860693088278438;*58<"),
                position()
                        .location("2026-10-25T19:44:46.000Z", true, -27.64323, -62.41282)
                        .attribute(Position.KEY_RPM, 0)
                        .attribute(Position.KEY_THROTTLE, 31.0)
                        .attribute(Position.KEY_OBD_ODOMETER, 111457000L)
                        .attribute(Position.KEY_COOLANT_TEMP, 72.0)
                        .attribute(Position.KEY_FUEL_LEVEL, null)
                        .attribute(Position.KEY_POWER, 12.9)
                        .attribute(Position.KEY_IGNITION, true)
                        .attribute(Position.KEY_SATELLITES, 9));

        verifyDecode(decoder, text(
                ">RER03251026185338-2764320-06241276000000FF001260000000013000700001099;2010!3,5000!3,1030!3,4201!3,1020!3,1010!3,2012!3,2013!3,2020!3,3010!3;#004C;ID=860693088278438;*2A<"),
                position()
                        .attribute(Position.KEY_RPM, null)
                        .attribute(Position.KEY_EVENT, 3));

        verifyDecode(decoder, text(
                ">RER26000000000000-0000000-000000000000007F0012600000000115000FFFF1512;2010!3,5000!3,1030!3,4201!3,1020!3,1010!3,2012!3,2013!3,2020!3,3010!3;#1D97;ID=860693088279170;*5C<"),
                position()
                        .attribute(Position.KEY_IGNITION, false)
                        .attribute(Position.KEY_EVENT, 0x26));

        verifyDecode(decoder, text(
                ">REQ00210918170359-2778100-064258570001517F000000000049D13010900001516;1=1M8GDM9A_KP042788,2=2200,3=45,B=66010,14=30000,15=75,2A=90,2C=340;ID=037883;*01<"),
                position()
                        .attribute(Position.KEY_VIN, "1M8GDM9A_KP042788")
                        .attribute(Position.KEY_RPM, 2200)
                        .attribute(Position.KEY_OBD_ODOMETER, 66010000L)
                        .attribute(Position.KEY_COOLANT_TEMP, 90));

        verifyDecode(decoder, text(
                ">REQ00000000000000-2780656-064296830000117F00000010836F1130112FFFF1117;1=,2=,3=,B=,14=,15=,2A=,2C=;ID=2326;*01<"),
                position()
                        .attribute(Position.KEY_RPM, null));

        verifyDecode(decoder, text(
                ">RCQ00211026183308-3148587-064241260003437F00000000000C413010800000099;#96BA;ID=860693088280376;*41<"),
                position()
                        .location("2026-10-21T18:33:08.000Z", true, -31.48587, -64.24126)
                        .attribute(Position.KEY_IGNITION, false)
                        .attribute(Position.KEY_INPUT, 0x7F)
                        .attribute(Position.KEY_ODOMETER, 196L)
                        .attribute(Position.KEY_SATELLITES, 8));

        verifyDecode(decoder, text(
                ">RAL19500+00230+00012;ID=3168;*48<"));

        verifyDecode(decoder, text(
                ">RLN19500000+337885218-0857685155+000753940000+000000000800000000000000000000000000000000000000000012;ID=3168;*4D<"));

        verifyDecode(decoder, text(
                ">RUS00,010170000000+0000000+000000000000001009999000011060074755268EF,0001139503871486,01,ZZZZZZZZZZ;ID=11817;#LOG:6AE4;*2C<"),
                position(Checks.ATTRIBUTES));

        verifyDecode(decoder, text(
                ">RPI041220132203-2683525-065204060150001050000101511140022118857EF27;ID=0000;#LOG:DECB;*07<"),
                position());

        verifyDecode(decoder, text(
                ">RCQ00151123235718-2782354-06407582055121FF0013501CDCC6313011100001514;#0805;ID=SIA056;*15<"),
                position());

        verifyDecode(decoder, text(
                ">RTT151123153149-4330468-06503640000009300DF2101 04101203 000 00000000130000040414;ID=8803;#1ABD;*2B<"));

        verifyDecode(decoder, text(
                ">RUS00,111220124402-3138067-06417623000012200FF,000000000000000000000000000,0000000111,15640422,00000,+25.5,00000,51;ID=CST3G0443;#IP1:089F;*34<"),
                position().attribute(Position.PREFIX_TEMP + 1, 25.5));

        verifyDecode(decoder, text(
                ">RUS00,031120185945-3138060-06417622000209200FF,000000000000000000000000000,0000000000,11440419,00000,00000,00000,00;ID=CST3G0495;#IP0:1EF7;*4B<"),
                position().attribute(Position.KEY_BATTERY, 4.19));

        verifyDecode(decoder, text(
                ">RUS00,031120185945-3138060-06417622000209200FF,000000000000000000000000000,0000000000,11440419,00000,00;ID=CST3G0495;#IP0:1EF7;*4B<"),
                position());

        verifyDecode(decoder, text(
                ">RGP041120190000-3137454-064075520001883004D50;ID=8385;#IP0:0080;*19<"),
                position());

        verifyDecode(decoder, text(
                ">RLN25601000+297185103-0955755990+000059150000+0000000012000000000000000000000000000000000000000000000000000000000012;ID=3580;*48<"));

        verifyDecode(decoder, text(
                ">RGP211217112154-2748332-058946350000000FF7F2100;ID=AA01;#0002;*2D<"),
                position());

        verifyDecode(decoder, text(
                ">RCV12270218010247-3471349-058400030002057F001200020A1D013010600001509+0000FF+0000FF;#1DE2;ID=7196;*03<"),
                position());

        verifyDecode(decoder, text(
                ">RPV03874+3477708-0923453100029212;ID=0017;*71<"),
                position());

        verifyDecode(decoder, text(
                ">RAL03874+00185+00012;ID=0017;*4A<"));

        verifyDecode(decoder, text(
                ">RCP03874+347771-092345312;ID=0017;*65<"));

        verifyDecode(decoder, text(
                ">RLN03874000+347770828-0923453071+000608270000+0000292309000000000000000000000000000000000000000000000012;ID=0017;*49<"));

        verifyDecode(decoder, text(
                ">RPV46640+4197412-0752857900015802;ID=5102;*71<"),
                position());

        verifyDecode(decoder, text(
                ">RCP46640+419741-075285802;ID=5102;*6C<"));

        verifyDecode(decoder, text(
                ">REV001958003965+0307178+1016144900031532;IO=300;SV=8;BL=4159;CF=8161,C,13;AD=14145;IX=10233040;FF=0,0,0,0;VO=338578;ID=357042063052352<"),
                position());

        verifyDecode(decoder, text(
                ">REV011958000369+0307185+1016144400000032;IO=200;SV=9;BL=4158;CF=0,0,0;AD=12347;IX=10213040;FF=0,0,0,0;VO=338572;ID=357042063052352<"),
                position());

        verifyDecode(decoder, text(
                ">REV421942237017+1170957-0701880200000032;ID=356612022463055<"),
                position());

        verifyDecode(decoder, text(
                ">RGP200317010815-3852.9306-06204.88560000003000101;&01;ID=5555;#7AD7*51<"),
                position());

        verifyDecode(decoder, text(
                ">RCQ09000000000000-3460365-058381460000007F0000000000000115000FFFF1099;#0000;ID=555224;*05<"),
                position());

        verifyDecode(decoder, text(
                ">RBR00130217040848-3462200-05846708000175FF0022900003B3C13010800001118410+24061A;ID=555224;*07<"),
                position());

        verifyDecode(decoder, text(
                ">REV451891352379+0307152+1016143700000012;SV=8;BL=4416;VO=8055;ID=356612026322000<"),
                position());

        verifyDecode(decoder, text(
                ">RGP230615010248-2682523-065236820000003007F4101;ID=0005;#0002;*2A<"),
                position().location("2015-06-23T01:02:48.000Z", true, -26.82523, -65.23682));

        verifyDecode(decoder, text(
                ">RGP190805211932-3457215-058493640000000FFBF0300;ID=8251;#2122;*54<"),
                position());

        verifyDecode(decoder, text(
                ">RPV00000+3739438-1220384601512612;ID=1234;*7F"),
                position());

        verifyDecode(decoder, text(
                "\r\n>REV691615354941+3570173+1397742703203212;ID=Test"),
                position());

        verifyDecode(decoder, text(
                ">REV481599462982+2578391-0802945201228512;ID=Test"),
                position().location("2010-09-02T17:29:42.000Z", true, 25.78391, -80.29452));

        verifyDecode(decoder, text(
                ">REV131756153215+3359479-0075299001031332;VO=10568798;IO=310;SV=10;BL=4190;CV09=0;AD=0;AL=+47;ID=356612021059680"),
                position());

        verifyDecode(decoder, text(
                ">RPV02138+4555512-0735478000000032;ID=1005;*76<"),
                position());

        verifyDecode(decoder, text(
                ">RPV19105+4538405-0739518900000012;ID=9999;*7A<\r\n"),
                position());

        verifyDecode(decoder, text(
                ">RUV0000,NT003,190826125922,00111595,39K40156,0B36 F,203 V,V1.5,JMAK,0,0,0,;ID=FWHS;#0004;*03<"),
                position().attribute(Position.KEY_VERSION_FW, "0B36 F"));

        verifyDecode(decoder, text(
                ">RUV01120,NT003,190826131202-2353539-046680190500009FFDE0006,00111596,0,0,0,0,50000,500,0,0,0,0,0,4G:0,00000;ID=FWHS;#1A04;*50<"),
                position().attribute(Position.KEY_ALARM, Position.ALARM_ACCELERATION));

        verifyDecode(decoder, text(
                ">RUV01111,NT003,190826132746-2353539-046680190000009FFDE0006,00111585,0,0,0,16,50000,500,0,0,50,0,0,4G:0,00000;ID=FWHS;#1A69;*5A<"),
                position().attribute(Position.KEY_ALARM, Position.ALARM_IDLE));

        verifyDecode(decoder, text(
                ">RUV02108,NT003,190826131734-2353539-046680190000009FF5E0006,384,0,0,0,0,385,0,323,6,50000,51515;ID=FWHS;#1A2A;*31<"),
                position().attribute(Position.KEY_FUEL_USED, 5151.5));

        verifyDecode(decoder, text(
                ">RUV03150,NT003,190826131748-2353539-046680190000009FFDE0006,0,6,50000,500,0,0,50,51515,0,0,0,0,0,0,0,0,0,0,0,0;ID=FWHS;#1A2D;*6D<"),
                position());

    }

}
