package com.example.bttofpga;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.BitSet;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {

    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public String btimg = "0";

    public BitSet bits = new BitSet(480*640*9+2);

    BluetoothAdapter btAdapter;
    private BluetoothSocket btSocket;

    boolean estado = false;

    Handler bluetoothIn;

    final int handlerState = 0;

    private StringBuilder DataStringIN = new StringBuilder();

    ConexionThread MyConexionBT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothIn = new Handler(){
            public void handleMessage(android.os.Message msg) {
                if (msg.what == handlerState) {
                    String readMessage = (String) msg.obj;
                    Toast.makeText(MainActivity.this, "Dato Recibido Entero: " + readMessage, Toast.LENGTH_SHORT).show();
                    DataStringIN.append(readMessage);

                    int endOfLineIndex = DataStringIN.indexOf("#");

                    if (endOfLineIndex > 0) {
                        String dataInPrint = DataStringIN.substring(0, endOfLineIndex);
                        //   Toast.makeText(MainActivity.this, "Dato Recibido: " +dataInPrint, Toast.LENGTH_SHORT).show();
                        DataStringIN.delete(0, DataStringIN.length());
                    }
                }
            }

        };


        Button pkbutton = findViewById(R.id.pkbutton);

        pkbutton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");

                btimg = "";

                startActivityForResult(intent.createChooser(intent, "Pick an Image"), 1);
            }
        });

        Button sndbutton = findViewById(R.id.sndbutton);



        sndbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                btAdapter = BluetoothAdapter.getDefaultAdapter();

                EditText macadd = findViewById(R.id.macadd);

                //Direccion mac del dispositivo a conectar
                BluetoothDevice device = btAdapter.getRemoteDevice(macadd.getText().toString());

                try
                {
                    //Crea el socket sino esta conectado
                    if(!estado)
                    {
                        btSocket = createBluetoothSocket(device);

                        estado = btSocket.isConnected();
                    }

                }
                catch (IOException e)
                {
                    Toast.makeText(getBaseContext(), "La creacción del Socket fallo", Toast.LENGTH_LONG).show();
                }

                // Establece la conexión con el socket Bluetooth.
                try
                {
                    //Realiza la conexion si no se a hecho
                    if(!estado)
                    {
                        btSocket.connect();
                        estado = true;
                        MyConexionBT = new ConexionThread(btSocket);
                        MyConexionBT.start();
                        Toast.makeText(MainActivity.this, "Conexion Realizada Exitosamente", Toast.LENGTH_SHORT).show();
                    }

                    else{
                        Toast.makeText(MainActivity.this, "Ya esta vinculado", Toast.LENGTH_SHORT).show();

                    }
                }

                catch (IOException e)
                {
                    try {
                        Toast.makeText(MainActivity.this, "Error:", Toast.LENGTH_SHORT).show();
                        Toast.makeText(MainActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
                        btSocket.close();
                    }
                    catch (IOException e2) {}
                }

                EditText editText = findViewById(R.id.textView);
                if(estado ) {
                    String dato = editText.getText().toString();
                    MyConexionBT.write(dato);
                    Toast.makeText(MainActivity.this, "Dato Enviado: " + dato, Toast.LENGTH_SHORT).show();
                }


                else {
                    Toast.makeText(MainActivity.this, "Solo se puede enviar datos si el dispositivo esta vinculado", Toast.LENGTH_SHORT).show();
                }

            }
        });


    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == RESULT_OK && requestCode == 1){

            try {
                Uri input = data.getData();
                startCrop(input);

            } catch (Exception e) {
                Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG);
            }
        }
        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {

            final Uri resultUri = UCrop.getOutput(data);

            ImageView imageView = findViewById(R.id.imageView);

            Bitmap bitmap = null;

            try {
                bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), resultUri);
            } catch (IOException e) {
                Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG);
            }

            imageView.setImageBitmap(bitmap);

            EditText editText = findViewById(R.id.textView);

            TextView len = findViewById(R.id.len);

            for (int i = 0; i < 100; i++) {
                for (int j = 0; j < 100; j++) {
                    btimg = btimg + RGBreturnALT(bitmap,j,i);
                }
            }

            editText.setText(btimg);

            len.setText("" + btimg.length());

        } else if (resultCode == UCrop.RESULT_ERROR) {
            final Throwable cropError = UCrop.getError(data);
        }
    }

    private void startCrop(Uri bitmap) {
        String desrfilename = new StringBuilder(UUID.randomUUID().toString()).append(".jpg").toString();

        UCrop ucrop = UCrop.of(bitmap, Uri.fromFile(new File(getCacheDir(),desrfilename)))
                .withAspectRatio(4, 3)
                .withMaxResultSize(640, 480);
        ucrop.start(MainActivity.this);
    }

    public String[] RGBreturn(Bitmap img, int x, int y){
        int pix = img.getPixel(x, y);
        String h[] = new String [3];
        h[0] = "00" + Integer.toBinaryString((int)(((pix>>16)&0xFF)*0.028));
        h[1] = "00" + Integer.toBinaryString((int)(((pix>>8)&0xFF)*0.028));
        h[2] = "00" + Integer.toBinaryString((int)((pix&0xFF)*0.028));

        h[0] = h[0].substring(h[0].length()-3,h[0].length());
        h[1] = h[1].substring(h[1].length()-3,h[1].length());
        h[2] = h[2].substring(h[2].length()-3,h[2].length());
        return h;
    }

    public char RGBreturnALT(Bitmap img, int x, int y){
        int pix = img.getPixel(x, y);
        int R,G,B;
        char PixelChar;
        R = (((pix>>21)&0x7)<<5);
        G = (((pix>>13)&0x7)<<2);
        B = ((pix>>6)&0x3);
        if (R == 0){
            R = 32;
        }
        if (G == 0){
            R = 4;
        }
        if (B == 0){
            R = 1;
        }

        PixelChar = (char)(R+G+B);
        return PixelChar;
    }


    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException
    {
        //crea un conexion de salida segura para el dispositivo
        //usando el servicio UUID
        return device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    }


    //Se debe crear una sub-clase para tambien heredar los metodos de CompaActivity y Thread juntos
//Ademas  en Run se debe ejecutar el subproceso(interrupcion)
    private class ConexionThread extends Thread
    {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConexionThread(BluetoothSocket socket)
        {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try
            {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            }
            catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run()
        {
            byte[] buffer = new byte[256];
            int bytes;

            while (true) {
                // Se mantiene en modo escucha para determinar el ingreso de datos
                try {
                    bytes = mmInStream.read(buffer);
                    String readMessage = new String(buffer, 0, bytes);
                    // Envia los datos obtenidos hacia el evento via handler
                    bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }

        //Enviar los datos
        public void write(String input)
        {
            try {
                mmOutStream.write(input.getBytes());
            }
            catch (IOException e)
            {
                //si no es posible enviar datos se cierra la conexión
                Toast.makeText(getBaseContext(), "La Conexión fallo", Toast.LENGTH_LONG).show();
                finish();
            }
        }




    }
}
