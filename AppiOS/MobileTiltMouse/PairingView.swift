import SwiftUI


/// Number of digits of pairing code
let codeSize = 5

/// A view for entering the pairing code to authenticate to the server.
///
/// This view displays a series of text fields for the user to input the pairing code.
/// It handles moving focus between text fields and submitting the code when all digits are entered.
/// It also displays an error message if the code is rejected and after a delay automatically clears the input for a new attempt.
///
/// - Parameters:
///     - pairing: An optional ``Pairing`` object to handle the pairing logic.
///     - pairingStatus: The status of the pairing process, here it is used for indicating if the code was rejected.
///
struct PairingView: View {
    var pairing: Pairing?
    var pairingStatus: PairingStatus
    
    @State private var code: [String] = Array(repeating: " ", count: codeSize)
    @FocusState private var focusedIndex: Int?
    
    var body: some View {
        VStack {
            if pairingStatus.codeRejected {
                Text("Invalid pairing code!")
                    .bold()
                    .foregroundStyle(.red)
                Text("Please try again.").padding(.bottom, 15)
            } else {
                Text("Please enter pairing code")
                Text("shown on computer:").padding(.bottom, 15)
            }
            
            HStack(spacing: 20) {
                ForEach(0..<codeSize, id: \.self) { idx in
                    TextField(" ", text: $code[idx])
                        .onChange(of: code[idx], initial: false) { oldValue, newValue  in
                            code[idx] = " "
                            if let digit = newValue.last {
                                if "0123456789".contains(digit) && newValue.count == 2 {
                                    code[idx] += String(digit)
                                    if idx < (codeSize - 1) {
                                        focusedIndex = idx + 1
                                    } else {
                                        focusedIndex = nil
                                        pairing?.sendPairingCode(code)
                                    }
                                }
                            } else {
                                // backspace/delete
                                if idx > 0 {
                                    focusedIndex = idx - 1
                                    code[idx - 1] = " "
                                }
                            }
                        }
                        .focused($focusedIndex, equals: idx)
                        .keyboardType(.numberPad)
                        .autocorrectionDisabled(true)
                        .padding(.leading, 10)
                        .frame(width: 40, height: 44)
                        .background() {
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(pairingStatus.codeRejected ? .red : .primary)
                        }
                        .foregroundStyle(pairingStatus.codeRejected ? .red : .primary)
                        .accessibilityIdentifier("Digit \(idx + 1)")
                }
            }
            // Only for UI tests add special elements that help in testing
            if ProcessInfo.processInfo.arguments.contains("-UITesting") {
                Button("UITestRejectCode") {
                    pairingStatus.codeRejected = true
                }
                if focusedIndex == nil {
                    Text("Final code:\(code.joined())")
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ignoresSafeArea(edges: .all)
        .background(Color.blue.opacity(0.2))
        .onChange(of: pairingStatus.codeRejected) { oldValue, newValue  in
            // code rejected, show it for a while before resetting pin entry
            if oldValue == false && newValue == true  {
                DispatchQueue.main.asyncAfter(deadline: .now() + .seconds(2), execute: {
                    code = Array(repeating: " ", count: codeSize)
                    focusedIndex = 0
                    pairingStatus.codeRejected = false
                })
            }
        }
        .onAppear {
            focusedIndex = 0
        }
    }
}
            
        
#Preview {
    PairingView(pairing: nil, pairingStatus: PairingStatus())
}
