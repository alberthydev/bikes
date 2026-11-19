import { Capacitor } from '@capacitor/core';
import { Camera, CameraResultType, CameraSource } from '@capacitor/camera';
import jsQR from 'jsqr';

/**
 * Decodifica um QR a partir de uma foto em data URL.
 * A imagem é reduzida para no máximo 1600 px, o que mantém a leitura rápida no celular.
 */
async function decodificar(dataUrl) {
    const imagem = new Image();
    imagem.src = dataUrl;
    await imagem.decode();

    const maiorLado = Math.max(imagem.naturalWidth, imagem.naturalHeight);
    const escala = Math.min(1, 1600 / maiorLado);
    const largura = Math.max(1, Math.round(imagem.naturalWidth * escala));
    const altura = Math.max(1, Math.round(imagem.naturalHeight * escala));

    const canvas = document.createElement('canvas');
    canvas.width = largura;
    canvas.height = altura;
    const contexto = canvas.getContext('2d', { willReadFrequently: true });
    contexto.drawImage(imagem, 0, 0, largura, altura);

    const pixels = contexto.getImageData(0, 0, largura, altura);
    const codigo = jsQR(pixels.data, largura, altura, { inversionAttempts: 'attemptBoth' });
    return codigo ? codigo.data : null;
}

async function lerQr() {
    const foto = await Camera.getPhoto({
        quality: 90,
        width: 1600,
        allowEditing: false,
        saveToGallery: false,
        resultType: CameraResultType.DataUrl,
        source: CameraSource.Camera
    });

    if (!foto.dataUrl) {
        throw new Error('A câmera não devolveu uma imagem.');
    }
    return decodificar(foto.dataUrl);
}

if (Capacitor.isNativePlatform()) {
    window.LocabikeCamera = {
        disponivel: true,
        lerQr
    };
}
