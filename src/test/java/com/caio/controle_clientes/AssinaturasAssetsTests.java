package com.caio.controle_clientes;

import javax.imageio.ImageIO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.HashSet;
import static org.assertj.core.api.Assertions.assertThat;

class AssinaturasAssetsTests {
    @ParameterizedTest
    @ValueSource(strings={"edson","ivani"})
    void preservaResolucaoTransparenciaGradualEVariacaoDaTinta(String nome) throws Exception {
        try(var in=getClass().getResourceAsStream("/assinaturas/"+nome+".png")) {
            assertThat(in).isNotNull(); var image=ImageIO.read(in);
            assertThat(image.getWidth()).isGreaterThanOrEqualTo(1000);
            assertThat(image.getHeight()).isGreaterThan(60);
            assertThat(image.getColorModel().hasAlpha()).isTrue();
            int transparentes=0,suaves=0; var cores=new HashSet<Integer>();
            for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                int pixel=image.getRGB(x,y),alpha=(pixel>>>24)&255;
                if(alpha==0) transparentes++;
                if(alpha>0 && alpha<255) suaves++;
                if(alpha>20) cores.add(pixel&0xffffff);
            }
            assertThat(transparentes).isGreaterThan(1000);
            assertThat(suaves).isGreaterThan(1000);
            assertThat(cores.size()).isGreaterThan(100);
        }
    }
}
