package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class GoogleMapUtils {

    private static final Logger LOGGER = LogManager.getLogger(GoogleMapUtils.class);

    // TODO: Add additional registration methods and listeners!

    private static final Map<String, String> REGISTRATION_METHOD_TO_LISTENER_MAPPING = new HashMap<>(){{
        put("Lcom/google/android/gms/maps/GoogleMap;->setOnMapLongClickListener(Lcom/google/android/gms/maps/GoogleMap$OnMapLongClickListener;)V",
                "Lcom/google/android/gms/maps/GoogleMap$OnMapLongClickListener;");
        put("Lcom/google/android/gms/maps/GoogleMap;->setOnInfoWindowClickListener(Lcom/google/android/gms/maps/GoogleMap$OnInfoWindowClickListener;)V",
                "Lcom/google/android/gms/maps/GoogleMap$OnInfoWindowClickListener;");
        put("Lcom/google/android/gms/maps/GoogleMap;->setOnMarkerClickListener(Lcom/google/android/gms/maps/GoogleMap$OnMarkerClickListener;)V",
                "Lcom/google/android/gms/maps/GoogleMap$OnMarkerClickListener;");
        put("Lcom/google/android/gms/maps/GoogleMap;->setOnCameraMoveListener(Lcom/google/android/gms/maps/GoogleMap$OnCameraMoveListener;)V",
                "Lcom/google/android/gms/maps/GoogleMap$OnCameraMoveListener;");
    }};

    private static final Map<String, String> LISTENER_TO_CALLBACK_MAPPING = new HashMap<>(){{
        put("Lcom/google/android/gms/maps/GoogleMap$OnMapLongClickListener;", "onMapLongClick(Lcom/google/android/gms/maps/model/LatLng;)V");
        put("Lcom/google/android/gms/maps/GoogleMap$OnInfoWindowClickListener;", "onInfoWindowClick(Lcom/google/android/gms/maps/model/Marker;)V");
        put("Lcom/google/android/gms/maps/GoogleMap$OnMarkerClickListener;", "onMarkerClick(Lcom/google/android/gms/maps/model/Marker;)Z");
        put("Lcom/google/android/gms/maps/GoogleMap$OnCameraMoveListener;", "onCameraMove()V");
    }};

    private GoogleMapUtils() {
        throw new UnsupportedOperationException("Utility class!");
    }

    // https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient#public-abstract-taskvoid-requestlocationupdates-locationrequest-request,-executor-executor,-locationcallback-callback
    public static boolean isRequestLocationUpdateInvocation(String methodSignature) {
        return methodSignature.equals("Lcom/google/android/gms/location/FusedLocationProviderClient;" +
                "->requestLocationUpdates(Lcom/google/android/gms/location/LocationRequest;" +
                "Lcom/google/android/gms/location/LocationCallback;Landroid/os/Looper;)Lcom/google/android/gms/tasks/Task;");
    }

    // https://developers.google.com/android/reference/com/google/android/gms/location/LocationCallback#public-void-onlocationavailability-locationavailability-availability
    public static String getOnLocationAvailabilityMethod(final String className) {
        return className + "->onLocationAvailability(Lcom/google/android/gms/location/LocationAvailability;)V";
    }

    // https://developers.google.com/android/reference/com/google/android/gms/location/LocationCallback#public-void-onlocationresult-locationresult-result
    public static String getOnLocationResultMethod(final String className) {
        return className + "->onLocationResult(Lcom/google/android/gms/location/LocationResult;)V";
    }

    /**
     * Retrieves the LocationCallback class for the requestLocationUpdates() invocation if possible.
     *
     * @param invokeInstruction The invoke instruction referring to the requestLocationUpdates() call.
     * @param classHierarchy A mapping from class name to its class.
     * @return Returns the LocationCallback class belonging to the requestLocationUpdates() invocation if possible, otherwise {@code null}.
     */
    public static String getLocationCallbackClass(final AnalyzedInstruction invokeInstruction, final ClassHierarchy classHierarchy) {

        if (invokeInstruction.getPredecessors().isEmpty()) {
            // couldn't backtrack invocation
            return null;
        }

        // Example:
        // iget-object v4, p0, Lnet/exclaimindustries/geohashdroid/activities/CentralMap;->mLocationCallback:Lcom/google/android/gms/location/LocationCallback;
        // const/4 v5, 0x0
        // invoke-virtual {v3, v1, v4, v5}, Lcom/google/android/gms/location/FusedLocationProviderClient;
        // ->requestLocationUpdates(Lcom/google/android/gms/location/LocationRequest;
        // Lcom/google/android/gms/location/LocationCallback;Landroid/os/Looper;)Lcom/google/android/gms/tasks/Task;
        //
        // constructor (write on same instance variable):
        // new-instance v1, Lnet/exclaimindustries/geohashdroid/activities/CentralMap$1;
        // invoke-direct {v1, p0}, Lnet/exclaimindustries/geohashdroid/activities/CentralMap$1;-><init>(Lnet/exclaimindustries/geohashdroid/activities/CentralMap;)V
        // iput-object v1, p0, Lnet/exclaimindustries/geohashdroid/activities/CentralMap;->mLocationCallback:Lcom/google/android/gms/location/LocationCallback;

        if (invokeInstruction.getInstruction() instanceof Instruction35c) {

            // The LocationCallback class is stored in the third register (v4 above) passed to the invoke instruction.
            final Instruction35c invoke = (Instruction35c) invokeInstruction.getInstruction();
            final int targetRegister = invoke.getRegisterE();

            AnalyzedInstruction pred = invokeInstruction.getPredecessors().first();

            // backtrack until we discover last write to register D (v1 above)
            while (pred.getInstructionIndex() != -1) {

                final Instruction predecessor = pred.getInstruction();

                if (pred.setsRegister(targetRegister) && predecessor.getOpcode() == Opcode.IGET_OBJECT) {

                    // iget-object v4, p0, Lnet/exclaimindustries/geohashdroid/activities/CentralMap;
                    //                     ->mLocationCallback:Lcom/google/android/gms/location/LocationCallback;
                    final String reference = ((ReferenceInstruction) predecessor).getReference().toString();
                    final String className = reference.split("->")[0];
                    final String variableName = reference.split("->")[1].split(":")[0];

                    final ClassDef classDef = classHierarchy.getClass(className);
                    if (classDef != null) {

                        // We assume that the instance variable is preferably written in one of the constructors
                        for (Method constructor : classDef.getDirectMethods()) {
                            final MethodImplementation implementation = constructor.getImplementation();
                            if (implementation != null) {
                                final List<Instruction> instructions = StreamSupport
                                        .stream(implementation.getInstructions().spliterator(), false)
                                        .collect(Collectors.toList());
                                int instructionIndex = 0;
                                for (Instruction instruction : implementation.getInstructions()) {
                                    // // check whether the instruction accesses (writes) the variable
                                    if (instruction.getOpcode() == Opcode.IPUT_OBJECT) {
                                        final String ref = ((ReferenceInstruction) instruction).getReference().toString();
                                        final String var = ref.split("->")[1].split(":")[0];
                                        if (variableName.equals(var)) {
                                            // We need to start backtracking from here.
                                            final Instruction22c iputObject = (Instruction22c) instruction;
                                            final int variableRegister = iputObject.getRegisterA(); // holds the variable
                                            for (int i = instructionIndex; i > 0; i--) {
                                                final Instruction precedingInstruction = instructions.get(i);
                                                if (precedingInstruction.getOpcode() == Opcode.NEW_INSTANCE
                                                        && ((Instruction21c) precedingInstruction).getRegisterA() == variableRegister) {
                                                    final String locationCallbackClass
                                                            = ((ReferenceInstruction) precedingInstruction).getReference().toString();
                                                    LOGGER.debug("Found LocationCallback class: " + locationCallbackClass);
                                                    return locationCallbackClass;
                                                }
                                            }
                                        }
                                    }
                                    instructionIndex++;
                                }
                            }
                        }
                    }
                }

                // consider next predecessor if available
                if (!pred.getPredecessors().isEmpty()) {
                    pred = pred.getPredecessors().first();
                } else {
                    break;
                }
            }
        }
        return null; // couldn't resolve invocation
    }

    // https://developers.google.com/maps/documentation/android-sdk/reference/com/google/android/libraries/maps/GoogleMap#nested-class-summary
    public static boolean isGoogleMapListenerInvocation(String methodSignature) {
        return REGISTRATION_METHOD_TO_LISTENER_MAPPING.containsKey(methodSignature);
    }

    /**
     * Retrieves the callback method associated with the given registration method if possible.
     *
     * @param registrationMethod The registration method of the listener, e.g., setOnInfoWindowClickListener().
     * @param invokeInstruction The invoke instruction referring to the listener registration.
     * @param classHierarchy A mapping from a class name to its class instance.
     * @return Returns the callback associated with the given registration method if possible, otherwise {@code null}.
     */
    public static String getListenerCallback(final String registrationMethod, final AnalyzedInstruction invokeInstruction,
                                             final ClassHierarchy classHierarchy) {

        if (invokeInstruction.getPredecessors().isEmpty()) {
            // couldn't backtrack invocation
            return null;
        }

        // Example:
        // iget-object v4, p0, Lnet/exclaimindustries/geohashdroid/activities/KnownLocationsPicker;
        //                     ->mMap:Lcom/google/android/gms/maps/GoogleMap;
        // invoke-virtual {v4, p0}, Lcom/google/android/gms/maps/GoogleMap;
        //                     ->setOnMapLongClickListener(Lcom/google/android/gms/maps/GoogleMap$OnMapLongClickListener;)V

        final String listener = REGISTRATION_METHOD_TO_LISTENER_MAPPING.get(registrationMethod);
        final String callback = LISTENER_TO_CALLBACK_MAPPING.get(listener);

        if (invokeInstruction.getInstruction() instanceof Instruction35c) {

            // The listener class is stored in the second register passed to the invoke instruction.
            final Instruction35c invoke = (Instruction35c) invokeInstruction.getInstruction();
            final int targetRegister = invoke.getRegisterD();

            AnalyzedInstruction pred = invokeInstruction.getPredecessors().first();

            // backtrack until we discover last write to register D (p0 above)
            while (pred.getInstructionIndex() != -1) {

                final Instruction predecessor = pred.getInstruction();

                if (pred.setsRegister(targetRegister) && predecessor.getOpcode() == Opcode.NEW_INSTANCE) {
                    final String className = ((ReferenceInstruction) predecessor).getReference().toString();
                    final ClassDef classDef = classHierarchy.getClass(className);
                    if (classDef != null && classDef.getInterfaces().contains(listener)) {
                        LOGGER.debug("Found callback: " + className + "->" + callback);
                        return className + "->" + callback;
                    }
                } else if (predecessor.getOpcode() == Opcode.IGET_OBJECT
                        && ((Instruction22c) predecessor).getRegisterB() == targetRegister) {
                    // iget-object v4, p0, Lnet/exclaimindustries/geohashdroid/activities/KnownLocationsPicker;
                    //                     ->mMap:Lcom/google/android/gms/maps/GoogleMap;
                    final String reference = ((ReferenceInstruction) predecessor).getReference().toString();
                    final String className = MethodUtils.getClassName(reference);
                    final ClassDef classDef = classHierarchy.getClass(className);
                    if (classDef != null && classDef.getInterfaces().contains(listener)) {
                        LOGGER.debug("Found callback: " + className + "->" + callback);
                        return className + "->" + callback;
                    }
                }

                // consider next predecessor if available
                if (!pred.getPredecessors().isEmpty()) {
                    pred = pred.getPredecessors().first();
                } else {
                    break;
                }
            }
        }
        return null; // couldn't resolve invocation
    }
}
